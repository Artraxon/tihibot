package de.rtrx.a

import com.uchuhimo.konf.ConfigSpec

object TihiConfig : ConfigSpec("tihi") {
    object CommentMonitor: ConfigSpec("commentMonitor"){
        val maxScore by required<Int>()
        val minCommentAmount by required<Int>()
        val messageBody by required<String>()
        val messageSubject by required<String>()
    }

    object Approval: ConfigSpec("approval"){
        val minScore  by required<Int>()
        val shameComment by required<String>()
    }

}