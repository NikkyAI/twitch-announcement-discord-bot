#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:1.1.0")

import io.github.typesafegithub.workflows.actions.actions.CheckoutV4
import io.github.typesafegithub.workflows.actions.docker.*
import io.github.typesafegithub.workflows.actions.nobrayner.DiscordWebhookV1
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile

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
    sourceFile = __FILE__.toPath(),
) {
    val buildJob = job(
        id = "build_job",
        name = "Build Job",
        runsOn = UbuntuLatest
    ) {
        uses(
            name = "Check out",
            action = CheckoutV4(
                fetchDepth = CheckoutV4.FetchDepth.Value(0)
            )
        )
        uses(
            name = "Docker Login",
            action = LoginActionV3(
        username = expr("secrets.DOCKER_HUB_USERNAME"),
        password = expr("secrets.DOCKER_HUB_ACCESS_TOKEN"),
    )
        )
        uses(
            name = "Docker Setup buildx",
            action = SetupBuildxActionV3()
        )
        val dockerBuildPush = uses(
            name = "Build and push",
            action = BuildPushActionV5(
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
