# twitch notifications discord bot

[![Discord](https://img.shields.io/discord/342696338556977153.svg?style=for-the-badge&logo=discord)](https://discord.gg/dQA3rYsYS8)
[![GitHub issues](https://img.shields.io/github/issues/NikkyAi/discordbot.svg?style=for-the-badge&logo=github)](https://github.com/NikkyAi/discordbot/issues)
[![GitHub issues](https://img.shields.io/github/issues/NikkyAi/discordbot.svg?style=for-the-badge&logo=github)](https://github.com/NikkyAi/discordbot/issues)
[![Docker Image Version (latest by date)](https://img.shields.io/docker/v/nikkyai/twitch-annoucement-discord-bot?style=for-the-badge)](https://hub.docker.com/repository/docker/nikkyai/twitch-annoucement-discord-bot)

## twitch notifications

the bot will not automatically get webhook management permissions because the usual setup for notification channels is to disallow `@everyone` from everything but seeing the channel
so you will need to add a override for the bot role (`Yuno`) anyways

to check if the permission setup is correct run  
`/twitch check`  
it will tell you the missing permissions

### add a twitch notification

`/twitch add role:@notifyme twitch:username`  
optionally you can pass `channe:#channel`, by default it uses the current channel

## role selection

to check if the permission setup is correct run  
`/role check`  
it will tell you the missing permissions

make sure that the bot role (`Yuno`) is above all of the roles that you want ot make selectable
(discord will not let the bot assign roles that are above itself in the list)

### add a role chooser

`/role add section:something reaction:❤ role:@role`  
optionally you can pass `channe:#channel`, by default it uses the current channel  

you can add multiple roles to the same section, they will be added to the same message


# run the bot

docker images: https://hub.docker.com/repository/docker/nikkyai/twitch-annoucement-discord-bot

see [docker-compose.yml](./docker-compose-sample.yml)

# dev setup

you will require a `.env` file to configure the bot with tokens and such
you can also make sure to have these available as env variables

```
CONFIG_DIR=data
TEST_GUILD=12354326587464353421

BOT_TOKEN=************************************************
TWITCH_CLIENT_ID=******************************
TWITCH_CLIENT_SECRET=****************************
```

you can run the bot with `./gradlew run` or `./gradlew runShadow`
