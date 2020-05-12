package de.rtrx.a

import mu.KotlinLogging
import net.dean.jraw.Endpoint
import net.dean.jraw.RedditClient
import net.dean.jraw.references.SubredditReference

/**
 * @param subreddit The name of the subreddit without the "/r/
 */
fun RedditClient.composeModMail(subreddit: String, subject: String, body: String) {
    val args = mapOf(
            "srName" to subreddit,
            "subject" to subject,
            "body" to body
    )
    val res = this.request {
        it.endpoint(Endpoint.POST_MOD_CONVERSATIONS).post(args)
    }
    KotlinLogging.logger {  }.trace { "Modmail sent with status code: " +  res.code }


}