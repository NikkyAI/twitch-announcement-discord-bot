#!/usr/bin/env kotlin

//@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.10.0")
@file:Repository("https://jitpack.io")
@file:DependsOn("com.github.nikkyai:github-actions-kotlin-dsl:9b41062015")

import it.krzeminski.githubactions.actions.actions.CheckoutV3
import it.krzeminski.githubactions.actions.docker.*
import it.krzeminski.githubactions.actions.nobrayner.DiscordWebhookV1
import it.krzeminski.githubactions.domain.RunnerType.UbuntuLatest
import it.krzeminski.githubactions.domain.triggers.PullRequest
import it.krzeminski.githubactions.domain.triggers.Push
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.dsl.expr
import it.krzeminski.githubactions.yaml.writeToFile

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
    sourceFile = __FILE__,
) {
    val buildJob = job(id = "build_job", name = "Build Job", runsOn = UbuntuLatest) {
        uses(
            name = "Check out",
            action = CheckoutV3(
                fetchDepth = CheckoutV3.FetchDepth.Value(0)
            )
        )
        uses(
            name = "Docker Login",
            action = LoginActionV2(
                username = expr("secrets.DOCKER_HUB_USERNAME"),
                password = expr("secrets.DOCKER_HUB_ACCESS_TOKEN"),
            )
        )
        uses(
            name = "Docker Setup buildx",
            action = SetupBuildxActionV2()
        )
        val dockerBuildPush = uses(
            name = "Build and push",
            action = BuildPushActionV3(
                context = ".",
                file = "./Dockerfile",
                push = true,
                tags = listOf("${expr("secrets.DOCKER_HUB_USERNAME")}/${expr("secrets.DOCKER_HUB_REPOSITORY")}:latest"),
            )
        )
        run(
            name = "image digest",
            command = "echo ${expr(dockerBuildPush.outputs.digest)}",
        )
    }
    job(
        id = "discord_notification",
        name = "Discord Notification",
        runsOn = UbuntuLatest,
        needs = listOf(
            buildJob
        ),
        condition = expr("always()"),
    ) {
        uses(
            name = "Discord Workflow Status Notifier",
            action = DiscordWebhookV1(
                githubToken = expr("github.token"),
                discordWebhook = expr("secrets.WEBHOOK_URL"),
                includeDetails = true,
            ),
            condition = "always()"
        )
    }
}

workflow.writeToFile(addConsistencyCheck = true)
