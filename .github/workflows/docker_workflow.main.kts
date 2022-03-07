#!/usr/bin/env kotlin

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.9.0")

import Docker_workflow_main.Util.secret
import it.krzeminski.githubactions.actions.Action
import it.krzeminski.githubactions.actions.actions.CacheV2
import it.krzeminski.githubactions.actions.actions.CheckoutV2
import it.krzeminski.githubactions.actions.docker.BuildPushActionV2
import it.krzeminski.githubactions.actions.docker.LoginActionV1
import it.krzeminski.githubactions.actions.docker.SetupBuildxActionV1
import it.krzeminski.githubactions.domain.RunnerType.UbuntuLatest
import it.krzeminski.githubactions.domain.triggers.PullRequest
import it.krzeminski.githubactions.domain.triggers.Push
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.yaml.toYaml
import it.krzeminski.githubactions.dsl.expr
import it.krzeminski.githubactions.yaml.writeToFile
import java.awt.Color
import java.nio.file.Paths
import kotlin.io.path.writeText

object Util {
    fun secret(secret: String): String = expr("secrets.$secret")
//    fun dollar(variable: String): String = "\$$variable"
}

val workflow = workflow(
    name = "Docker build & push",
    on = listOf(
        Push(
            branches = listOf("main"),
        ),
        PullRequest(
            branches = listOf("main"),
        )
    ),
    sourceFile = Paths.get(".github/workflows/docker_workflow.main.kts"),
    targetFile = Paths.get(".github/workflows/docker_workflow.yml"),
) {
    val buildJob = job(name = "build_job", runsOn = UbuntuLatest) {
        run(name = "Print workflow name", command = "echo 'Hello '\$GITHUB_WORKFLOW'!'")
        uses(
            name = "Check out",
            action = CheckoutV2(
                fetchDepth = CheckoutV2.FetchDepth.Value(0)
            )
        )
        uses(
            name = "Cache",
            action = CacheV2(
                path = listOf(
                    "~/.gradle",
                    ".gradle",
                    "~/.docker",
                ),
                key = "${expr("runner.os")}-gradle-cache-${expr("github.ref")}-${expr("hashFiles('**/build.gradle.kts', 'version.properties')")}"
            )
        )
        uses(
            name = "Docker Login",
            action = LoginActionV1(
                username = secret("DOCKER_HUB_USERNAME"),
                password = secret("DOCKER_HUB_ACCESS_TOKEN"),
            )
        )
        uses(
            name = "Docker Setup buildx",
            action = SetupBuildxActionV1()
        )
        val dockerBuildPush = uses(
            name = "Build and push",
//            id = "docker_build_push",
            action = BuildPushActionV2(
                context = ".",
                file = "./Dockerfile",
                push = true,
                tags = listOf("${secret("DOCKER_HUB_USERNAME")}/${secret("DOCKER_HUB_REPOSITORY")}:latest"),
            )
        )
        run(
            name = "image digest",
            command = "echo ${expr(dockerBuildPush.outputs.digest)}",
        )
    }
    job(
        name = "discord-notification",
        runsOn = UbuntuLatest,
        needs = listOf(
            buildJob
        ),
        condition = expr("always()"),
    ) {
        uses(
            name = "Discord Workflow Status Notifier",
            action = DiscordWebhook(
                webhookUrl = secret("WEBHOOK_URL"),
                includeDetails = true,
            ),
            condition = "always()"
        )
    }
}

class DiscordWebhook(
    val webhookUrl: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val includeDetails: Boolean? = null,
    val colorSuccess: Color? = null,
    val colorFailure: Color? = null,
    val colorCancelled: Color? = null,
    val githubToken: String? = null,
) : Action("nobrayner", "discord-webhook", "v1") {
    override fun toYamlArguments(): LinkedHashMap<String, String> = linkedMapOf(
        "github-token" to (githubToken ?: expr("github.token")),
        "discord-webhook" to webhookUrl,
        *listOfNotNull(
            username?.let {
                "username" to it
            },
            avatarUrl?.let {
                "avatar-url" to it
            },
            title?.let {
                "title" to it
            },
            description?.let {
                "description" to it
            },
            includeDetails?.let {
                "include-details" to it.toString()
            },
            colorSuccess?.let {
                "color-success" to "#" + Integer.toHexString(it.rgb).substring(2)
            },
            colorFailure?.let {
                "color-failure" to "#" + Integer.toHexString(it.rgb).substring(2)
            },
            colorCancelled?.let {
                "color-cancelled" to "#" + Integer.toHexString(it.rgb).substring(2)
            }
        ).toTypedArray()
    )
}

val yaml = workflow.toYaml(addConsistencyCheck = true)
workflow.writeToFile()
if (args.contains("--save")) {
    workflow.targetFile.writeText(yaml + "\n")
} else if(args.isEmpty()) {
    println(yaml)
} else {
    println("unknown args: ${args.joinToString(" ") {"'$it'"}}")
}