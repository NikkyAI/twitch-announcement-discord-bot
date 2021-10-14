# twitch notifications discord bot

## run the bot

see [docker-compose.yml](./docker-compose.yml)

## dev setup

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
