# twitch notifications discord bot
## twitch notifications

to check if the permission setup is correct run  
`/twitch check`

### add a twitch notification

`/twitch add role:@notifyme twitch:username`  
optionally you can pass `channe:#channel`, by default it uses the current channel

## role selection

to check if the permission setup is correct run  
`/role check`

make sure that the bot role (`Yuno`) is above all of the roles that you want ot make selectable
(discord will not let the bot assign roles that are above itself in the list)

### add a role chooser

`/role add section:something reaction:❤ role:@role`  
optionally you can pass `channe:#channel`, by default it uses the current channel  

you can add multiple roles to the same section, they will be added to the same message


# run the bot

docker images: https://hub.docker.com/repository/docker/nikkyai/discordbot
see [docker-compose.yml](./docker-compose.yml)

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
