Places Platform API
=========================

This is the RESTful API for a places platform.

The project was initially the backend for a mobile places app (originally called SceneKap), but after development was halted, it has been released as open source. It utilizes the Play Framework, Scala, HTML, CSS, and MongoDB.

The project comes included with an interface allowing easy manipulation of the API for testing purposes via a web browser.

Quickstart Guide
=========================

Get the project
---------------

```bash
git clone https://github.com/Kapware64/places-platform.git
```

Edit Configuration
-----------------
In order to run the server, all empty fields in application.conf must be filled in.

Run the project
---------------
Once the above configurations are completed, the project can be run on your local host as a Play 2 App. However, before running it remotely, you should implement an API key and SSL.

Features
=========================
This platform has the following features and more:
* Users
    * Get user
        *  Each user stores username, password, email, deviceID, and user score
    * Username/password login
    * Password recovery
    * First login
    * New device login
    * Create user
    * User score
        * Based on how active the user is
        * The higher the score, the more upvote/downvote power the user has
* Places
    * Get place
        * Retrieves summary, comments, and photos
            * Each comment/photo has vote value
    * Get nearby places
    * Comments and Photos
        * Upvote/downvote
        * Get recent
        * Get top
        * Add
        * Remove
* Website
    * Generate website summary

All place data is initially pulled from Google's Places API and is cached when possible to reduce the number of calls to Google's Places API. Whenever a piece of user-entered data can replace a piece of data from Google's Places API (this is determined by the relevancy of the user-entered data and its the upvote/downvote history), then it will always be used in place of calling Google's Place API for that item of data.

Another feature of this platform is its place summary generation. The platform maintains updated summaries of each place utilizing its website summarization tool and places' websites. Utilizing a list of "stop words", "bad words", and "filler" words to direct its summary decision making, the platform ensures the summary of each place stays relevant.