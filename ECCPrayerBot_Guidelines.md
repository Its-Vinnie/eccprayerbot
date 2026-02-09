# ECCPrayerBot Documentation & User Guidelines

## 1. Introduction
ECCPrayerBot is a Telegram bot designed to provide quick access to Bible verses from multiple translations. It integrates with YouVersion (Digital Bible Platform) and API.Bible (Scripture API) to offer a wide range of Bible versions, including premium and public domain ones.

## 2. Features
- **Single Verse Retrieval**: Fetch any specific Bible verse.
- **Verse Range**: Fetch multiple consecutive verses (e.g., John 3:16-18).
- **Whole Chapters**: Fetch an entire chapter at once.
- **Multiple Translations**: Choose from 13+ popular Bible translations.
- **Group & Channel Support**: Works in private chats, groups, and channels.

## 3. Supported Translations
The bot automatically routes requests to the appropriate provider based on the requested translation.

### Premium Versions (API.Bible Pro & YouVersion)
The bot now primarily uses API.Bible Pro for high-performance retrieval of major translations. YouVersion serves as a secondary provider for specific versions.

- **NIV** - New International Version
- **NKJV** - New King James Version
- **NLT** - New Living Translation
- **ESV** - English Standard Version
- **AMP** - Amplified Bible
- **AMPC** - Amplified Bible Classic
- **MSG** - The Message
- **TPT** - The Passion Translation (YouVersion)
- **EASY** - Holy Bible: Easy-to-Read Version (YouVersion)

### Public Domain & Specialized Versions (API.Bible)
- **KJV** - King James Version
- **ASV** - American Standard Version
- **WEB** - World English Bible
- **WEBBE** - World English Bible British Edition
- **FBV** - Free Bible Version
- **RSV** - Revised Standard Version
- **GNT** - Good News Translation
- **DRA** - Douay-Rheims American 1899
- **GNV** - Geneva Bible
- **TCNT** - Twentieth Century New Testament
- **RV** - Revised Version 1885

## 4. How to Use

### Basic Command Syntax
To get a verse, mention the bot followed by the Bible reference:
`@eccprayerbot [Book] [Chapter]:[Verse] [Translation]`

### Examples
- **Single Verse**: `@eccprayerbot John 3:16` (Defaults to KJV)
- **Verse Range**: `@eccprayerbot Genesis 1:1-3`
- **Specific Translation**: `@eccprayerbot Romans 8:28 NIV`
- **Whole Chapter**: `@eccprayerbot Psalm 23`
- **Using Abbreviations**: `@eccprayerbot Mt 28:19-20`

### Usage in Different Environments
1. **Private Chat**: Simply send the reference to the bot.
2. **Groups**: Mention the bot (`@eccprayerbot`) followed by the reference.
3. **Channels**: 
   - Add the bot as an Administrator to your channel.
   - To trigger the bot, a post must contain the bot's mention (e.g., `@eccprayerbot John 3:16`).

## 5. Troubleshooting
- **Bot doesn't respond in Channel**: Ensure the bot is added as an **Administrator** with permission to post messages.
- **Invalid Reference**: Make sure the book name is spelled correctly or use a standard abbreviation.
- **Translation not found**: If a translation is not supported, the bot will default to **KJV**.

## 6. Technical Overview (For Administrators)
- **Framework**: Spring Boot 3.2.1
- **Database**: MongoDB (for request logging and caching)
- **Resilience**: Integrated with Resilience4j Circuit Breakers to handle API downtime gracefully.
- **Caching**: Verses are cached for faster subsequent retrieval and to reduce API calls.
