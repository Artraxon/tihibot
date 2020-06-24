package de.rtrx.a.tihi

import com.uchuhimo.konf.Config
import de.rtrx.a.RedditSpec
import de.rtrx.a.flow.events.comments.FullComments
import de.rtrx.a.monitor.Check
import de.rtrx.a.monitor.Monitor
import de.rtrx.a.monitor.MonitorBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import net.dean.jraw.RedditClient
import net.dean.jraw.models.Comment
import net.dean.jraw.references.SubmissionReference
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Provider

interface CommentsHookedMonitor: Monitor {
    suspend fun acceptData(fullComments: FullComments)
}
interface CommentsHookedMonitorBuilder: MonitorBuilder<CommentsHookedMonitor>

interface BotCommentAction {
    suspend fun act(submission: SubmissionReference, botComment: Comment, fullComments: FullComments)
}

class NotifyModerators @Inject constructor(
        private val redditClient: RedditClient,
        private val config: Config
) : BotCommentAction {
    override suspend fun act(submission: SubmissionReference, botComment: Comment, fullComments: FullComments) {

        val replacements: Map<String, () -> String> = mapOf(
                "commentScore" to { fullComments.sticky?.score?.toString() ?: "null" },
                "submissionLink" to { submission.inspect().permalink },
                "submissionID" to { submission.id },
                "author" to { submission.inspect().author },
                "submissionScore" to { submission.inspect().score.toString() },
                "childrenCount" to { fullComments.commentsToSticky.toString() },
                "submissionTitle" to { submission.inspect().title }
                ).mapKeys {(key, _ )-> "%{$key}" }

        redditClient.composeModMail(
                config[RedditSpec.subreddit],
                config[TihiConfig.CommentMonitor.messageSubject].replaceAll(replacements),
                config[TihiConfig.CommentMonitor.messageBody].replaceAll(replacements)
        )
    }

}

class BotCommentMonitorBuilder @Inject constructor(
        private val config: Config,
        private val actionProvider: Provider<BotCommentAction>
): CommentsHookedMonitorBuilder {
    lateinit var botComment: Comment
    override fun build(submission: SubmissionReference): CommentsHookedMonitor {
        return BotCommentMonitor(
                submission,
                botComment,
                config[TihiConfig.CommentMonitor.maxScore],
                config[TihiConfig.CommentMonitor.minCommentAmount],
                actionProvider.get()
        )
    }

    override fun setBotComment(comment: Comment?): MonitorBuilder<CommentsHookedMonitor> {
        this.botComment = comment ?: throw IllegalArgumentException("This Monitor neeeds a bot Comment")
        return this
    }

}
class BotCommentMonitor(
        submission: SubmissionReference,
        botComment: Comment,
        private val maxScore: Int,
        private val minComments: Int,
        private val action: BotCommentAction
        ) : Check(submission, botComment), CommentsHookedMonitor {
    private val context = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var wasSent = false

    override suspend fun acceptData(fullComments: FullComments) {
        CoroutineScope(context).launch {
            if(wasSent) return@launch
            if(fullComments.sticky != null && botComment!!.id == fullComments.sticky!!.id){
                if(fullComments.sticky!!.score < maxScore || fullComments.commentsToSticky >= minComments ){
                    action.act(submission, botComment, fullComments)
                    wasSent = true
                }
            }
        }
    }

    override suspend fun start() {
        if(botComment == null) throw IllegalArgumentException("BotComment Monitor needs a botComment")
    }
}

fun String.replaceAll(replacings: Map<String, () -> String>): String {
    return replacings.entries.fold(this) { current, (toReplace, replacement) -> current.replace(toReplace, replacement()) }
}