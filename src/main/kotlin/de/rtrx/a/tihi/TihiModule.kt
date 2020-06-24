package de.rtrx.a.tihi

import com.google.inject.Provides
import com.google.inject.name.Names
import com.uchuhimo.konf.Config
import de.rtrx.a.database.DDL
import de.rtrx.a.database.Linkage
import de.rtrx.a.database.PostgresSQLinkage
import de.rtrx.a.flow.*
import de.rtrx.a.flow.events.comments.CommentsFetcherFactory
import de.rtrx.a.flow.events.comments.ManuallyFetchedEvent
import de.rtrx.a.tihi.database.TIHIDDL
import de.rtrx.a.tihi.database.TIHILinkage
import de.rtrx.a.unex.UnexFlow
import de.rtrx.a.unex.UnexFlowFactory
import dev.misfitlabs.kotlinguice4.KotlinModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import net.dean.jraw.references.SubmissionReference
import javax.inject.Named

class TihiModule : KotlinModule(){

    @Provides
    fun provideDispatcherStub(
            newPosts: ReceiveChannel<SubmissionReference>,
            flowFactory: TihiFlowFactory,
            @Named("launcherScope") launcherScope: CoroutineScope,
            manuallyFetchedFactory: CommentsFetcherFactory
    ) : IFlowDispatcherStub<TihiFlow, TihiFlowFactory> = FlowDispatcherStub(newPosts, flowFactory, launcherScope,
            mapOf( ManuallyFetchedEvent::class to (manuallyFetchedFactory to SubmissionReference::class) ) as EventFactories)

    @Provides
    @Named("functions")
    fun provideDDLFunctions(config: Config) = with(DDL.Companion.Functions){
        listOf(
                addParentIfNotExists,
                TIHIDDL.createComment,
                commentWithMessage,
                TIHIDDL.createCheck,
                redditUsername
        ).map { it(config) }}

    @Provides
    @Named("tables")
    fun provideDDLTable(config: Config) = with(DDL.Companion.Tables) { listOf(
            submissions,
            TIHIDDL.commentsTable,
            relevantMessages,
            comments_caused,
            commentsHierarchy,
            check,
            unexScore,
            TIHIDDL.topPosts
    ).map { it(config) }}

    override fun configure() {
        bind(Replyer::class.java).annotatedWith(Names.named("shameReply"))
                .to(ShameReply::class.java)

        bind(PostgresSQLinkage::class.java)

        bind(BotCommentAction::class.java).to(NotifyModerators::class.java)
        bind(CommentsHookedMonitorBuilder::class.java).to(BotCommentMonitorBuilder::class.java)
        bind(DeletePrevention::class.java).toProvider(ApprovalAndScoreCheckFactory::class.java)

        bind(CoroutineScope::class.java).annotatedWith(Names.named("launcherScope"))
                .toInstance(CoroutineScope(Dispatchers.Default))
        bind(TihiFlowFactory::class.java).to(RedditTihiFlowFactory::class.java)
        bind(TihiFlowDispatcher::class.java)

    }
}