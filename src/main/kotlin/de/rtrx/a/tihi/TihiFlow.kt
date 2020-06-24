package de.rtrx.a.tihi

import com.uchuhimo.konf.Config
import de.rtrx.a.RedditSpec
import de.rtrx.a.database.Linkage
import de.rtrx.a.flow.*
import de.rtrx.a.flow.events.EventMultiplexerBuilder
import de.rtrx.a.flow.events.EventType
import de.rtrx.a.flow.events.IncomingMessagesEvent
import de.rtrx.a.flow.events.SentMessageEvent
import de.rtrx.a.flow.events.comments.FullComments
import de.rtrx.a.flow.events.comments.ManuallyFetchedEvent
import de.rtrx.a.monitor.IDBCheckBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import mu.KotlinLogging
import net.dean.jraw.RedditClient
import net.dean.jraw.models.Comment
import net.dean.jraw.models.DistinguishedStatus
import net.dean.jraw.models.Submission
import net.dean.jraw.references.CommentReference
import net.dean.jraw.references.PublicContributionReference
import net.dean.jraw.references.SubmissionReference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

class TihiFlow (
        flowStub: FlowStub<SubmissionReference, TihiFlow>,
        private val callback: Callback<in FlowResult<TihiFlow>, Unit>,
        private val composingFn: MessageComposer,
        private val replyFn: Replyer,
        private val shameReply: Replyer,
        private val sentMessages: SentMessageEvent,
        private val incomingMessages: IncomingMessagesEvent,
        private val manuallyFetchedEvent: ManuallyFetchedEvent,
        private val linkage: Linkage,
        private val dbCheckBuilder: IDBCheckBuilder,
        private val commentsHookedMonitorBuilder: CommentsHookedMonitorBuilder,
        private val conversation: Conversation,
        private val delayedDeleteFactory: DelayedDeleteFactory,
        private val config: Config
): IFlowStub<SubmissionReference> by flowStub, Flow{
    private val logger = KotlinLogging.logger {  }

    override suspend fun start() {
        launch {
            try {
                logger.trace("Starting flow for ${initValue.fullName}")
                if (linkage.insertSubmission(initValue.inspect()) == 0) {
                    logger.trace("Cancelling flow for ${initValue.fullName} because the submission is already present")
                    callback(SubmissionAlreadyPresent(this@TihiFlow))
                    return@launch
                }
                val awaitedReply = async { conversation.run { waitForCompletion(produceCheckMessage(initValue.id)) } }
                subscribe(conversation::start, sentMessages)
                subscribe(conversation::reply, incomingMessages)

                composingFn(initValue.inspect().author, initValue.inspect().permalink)
                val deletion = delayedDeleteFactory.create(initValue, this)
                deletion.start()
                val answered = deletion.safeSelectTo(awaitedReply.onAwait)

                if (!answered.bool) {
                    callback(NoAnswerReceived(this@TihiFlow))
                    return@launch
                }
                val (comment, ref) =
                        if (answered is ApprovalAndScoreCheckFactory.NotDeletedSufficientScore) {
                            initValue.flair(config[RedditSpec.subreddit]).updateToCssClass("shame", "SHAME")
                            shameReply(initValue.inspect(), "")
                        } else {
                            replyFn(initValue.inspect(), awaitedReply.getCompleted().body)
                                    .also { (comment, _) ->
                                        linkage.commentMessage(initValue.id, awaitedReply.await(), comment)
                                    }
                        }


                ref.distinguish(DistinguishedStatus.MODERATOR, true)

                logger.trace("Starting the monitors for ${initValue.fullName}")
                val dbCheckMonitor = dbCheckBuilder.setCommentEvent(manuallyFetchedEvent)
                        .setBotComment(comment)
                        .build(initValue)

                val hookedMonitor = commentsHookedMonitorBuilder
                        .setBotComment(comment)
                        .build(initValue)
                subscribe(dbCheckMonitor::saveToDB, manuallyFetchedEvent)
                subscribe(hookedMonitor::acceptData, manuallyFetchedEvent)
                hookedMonitor.start()
                dbCheckMonitor.start()

                callback(FlowResult.NotFailedEnd.RegularEnd(this@TihiFlow))

            } catch (c: CancellationException){
                callback(FlowResult.FailedEnd.Cancelled(this@TihiFlow))
                logger.warn("Flow for submission ${initValue.fullName} was cancelled")
            }
        }
    }
}

class ApprovalAndScoreCheckFactory @Inject constructor(
        private val linkage: Linkage,
        private val config: Config
): Provider<DeletePrevention> {
    override fun get(): DeletePrevention {
        return object : DeletePrevention {
            override suspend fun check(publicRef: PublicContributionReference): DelayedDelete.DeleteResult {
                return linkage.createCheckSelectValues(
                        publicRef.fullName,
                        null,
                        null,
                        emptyArray(),
                        {
                            if (it.get("approved")?.asBoolean ?: false) {
                                DelayedDelete.Companion.NotDeletedApproved()
                            } else {
                                if (it.get("score")?.asInt ?: 0 >= config[TihiConfig.Approval.minScore]) NotDeletedSufficientScore()
                                else DelayedDelete.DeleteResult.WasDeleted()
                            }
                        }
                ).checkResult as DelayedDelete.DeleteResult
            }
        }
    }
    class NotDeletedSufficientScore : DelayedDelete.DeleteResult.NotDeleted()
}
interface TihiFlowFactory : FlowFactory<TihiFlow, SubmissionReference>{
    fun setSentMessages(sentMessages: SentMessageEvent)
    fun setIncomingMessages(incomingMessages: IncomingMessagesEvent)
}


class RedditTihiFlowFactory @Inject constructor(
        private val config: Config,
        private val composingFn: MessageComposer,
        private val replyFn: Replyer,
        @param:Named("shameReply") private val shameReply: Replyer,
        private val dbCheckFactory: Provider<IDBCheckBuilder>,
        private val botCommentMonitorFactory: Provider<CommentsHookedMonitorBuilder>,
        private val multiplexerProvider: Provider<EventMultiplexerBuilder<FullComments, *, ReceiveChannel<FullComments>>>,
        private val conversationFactory: Provider<Conversation>,
        private val delayedDeleteFactory: DelayedDeleteFactory,
        private val linkage: Linkage
) : TihiFlowFactory {
    private lateinit var sentMessages: SentMessageEvent
    private lateinit var incomingMessages: IncomingMessagesEvent


    override suspend fun create(dispatcher: FlowDispatcherInterface<TihiFlow>, initValue: SubmissionReference, callback: Callback<FlowResult<TihiFlow>, Unit>): TihiFlow {
        val stub = FlowStub(
                initValue,
                { TihiFlow: TihiFlow, fn: suspend (Any) -> Unit, type: EventType<Any> ->
                    dispatcher.subscribe(TihiFlow, fn, type)
                },
                dispatcher::unsubscribe,
                CoroutineScope(Dispatchers.Default)
        )
        val flow = TihiFlow(
                stub,
                callback,
                composingFn,
                replyFn,
                shameReply,
                sentMessages,
                incomingMessages,
                dispatcher.createNewEvent(ManuallyFetchedEvent::class, initValue, multiplexerProvider.get()),
                linkage,
                dbCheckFactory.get(),
                botCommentMonitorFactory.get(),
                conversationFactory.get(),
                delayedDeleteFactory,
                config
        )
        stub.setOuter(flow)
        return flow
    }


    override fun setSentMessages(sentMessages: SentMessageEvent) {
        if (!this::sentMessages.isInitialized) this.sentMessages = sentMessages
    }

    override fun setIncomingMessages(incomingMessages: IncomingMessagesEvent) {
        if (!this::incomingMessages.isInitialized) this.incomingMessages = incomingMessages
    }
}

class ShameReply @Inject constructor(
        private val redditClient: RedditClient,
        private val config: Config): Replyer {
    override fun invoke(submission: Submission, reason: String): Pair<Comment, CommentReference> {
        val comment = submission.toReference(redditClient)
                .reply(config[TihiConfig.Approval.shameComment])
        return comment to comment.toReference(redditClient)
    }
}
