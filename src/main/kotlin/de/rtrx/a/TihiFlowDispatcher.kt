package de.rtrx.a

import de.rtrx.a.flow.IFlowDispatcherStub
import de.rtrx.a.flow.IsolationStrategy
import de.rtrx.a.flow.events.*
import de.rtrx.a.flow.events.comments.FullComments
import de.rtrx.a.unex.UnexFlow
import de.rtrx.a.unex.UnexFlowFactory
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.dean.jraw.models.Message
import javax.inject.Inject
import javax.inject.Provider

class TihiFlowDispatcher @Inject constructor(
        private val stub: IFlowDispatcherStub<TihiFlow, TihiFlowFactory>,
        incomingMessageMultiplexerBuilder: @JvmSuppressWildcards EventMultiplexerBuilder<Message, @JvmSuppressWildcards EventMultiplexer<Message>, @JvmSuppressWildcards ReceiveChannel<Message>>,
        sentMessageMultiplexerBuilder: @JvmSuppressWildcards EventMultiplexerBuilder<Message, @JvmSuppressWildcards EventMultiplexer<Message>, @JvmSuppressWildcards ReceiveChannel<Message>>,
        incomingMessageFactory: IncomingMessageFactory,
        sentMessageFactory: SentMessageFactory,
        isolationStrategy: IsolationStrategy,
        markAsReadFlow: MarkAsReadFlow
) : IFlowDispatcherStub<TihiFlow, TihiFlowFactory> by stub{

    private val incomingMessageMultiplexer: EventMultiplexer<Message>
    private val sentMessageMultiplexer: EventMultiplexer<Message>

    private val incomingMessagesEvent: IncomingMessagesEvent
    private val sentMessageEvent: SentMessageEvent

    init {
        val (incomingEvent, incomingChannel) = incomingMessageFactory.create("")
        val (sentEvent, sentChannel) = sentMessageFactory.create("")
        this.incomingMessagesEvent = incomingEvent
        this.sentMessageEvent = sentEvent

        stub.flowFactory.setIncomingMessages(incomingMessagesEvent)
        stub.flowFactory.setSentMessages(sentMessageEvent)

        incomingMessageMultiplexer = incomingMessageMultiplexerBuilder
                .setOrigin(incomingChannel)
                .setIsolationStrategy(isolationStrategy)
                .build()
        sentMessageMultiplexer = sentMessageMultiplexerBuilder
                .setOrigin(sentChannel)
                .setIsolationStrategy(isolationStrategy)
                .build()

        runBlocking {
            stub.registerMultiplexer(incomingMessagesEvent, incomingMessageMultiplexer)
            stub.registerMultiplexer(sentMessageEvent, sentMessageMultiplexer)
        }
        incomingMessageMultiplexer.addListener(markAsReadFlow, markAsReadFlow::markAsRead)
        stub.start()
        KotlinLogging.logger { }.info("Started TihiFlow Dispatcher")
    }
}
