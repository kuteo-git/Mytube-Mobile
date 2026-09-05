package com.mytube.app.ui.i18n

import androidx.compose.runtime.compositionLocalOf

/**
 * Every word this app shows a person.
 *
 * ## Why an interface and not a key lookup
 *
 * The web side of this system spent a release learning that half-translating is
 * the failure mode, and built three guard layers against it. The first is typed
 * keys, so `t('nav.acount')` does not compile. This is the same idea taken one
 * step further: there are no keys at all. A string is a property, so **adding
 * one here breaks every language that has not supplied it**, at compile time,
 * with the compiler naming the class and the property.
 *
 * A resource file with keys can always be missing a key. An interface cannot.
 *
 * ## Why not Compose Resources
 *
 * Compose Multiplatform ships a string-resource mechanism, and it would work.
 * It also means XML, a code-generation step, and a lookup that fails at runtime
 * when a key is absent from one language — trading the guarantee above for a
 * familiar file format. The guarantee is worth more here.
 *
 * ## The rule this file exists to keep
 *
 * *"All source code, identifiers, comments and commit messages MUST be in
 * English"* — and separately, in-app copy is translated. English is the source:
 * every value in [EnglishStrings] is authored, and [VietnameseStrings] is a
 * translation kept beside it.
 *
 * A translation is **written, not converted**. Vietnamese drops the subject
 * wherever context carries it and English cannot, so carrying every "the" and
 * "it" across is the surest sign of a machine.
 */
interface Strings {

    // --- server setup ------------------------------------------------------
    val appName: String
    val setupSubtitle: String
    val serverAddressLabel: String
    val serverAddressHint: String
    val checkUntested: String
    val checkChecking: String
    val checkReachable: String
    val checkFailed: String
    val check: String
    val save: String

    // --- home --------------------------------------------------------------
    val noServerTitle: String
    val setTheAddress: String
    val couldNotReach: String
    val tryAgain: String
    val moreOptions: String
    val continueWatching: String
    val chipAll: String

    /** New uploads from followed channels, not yet watched. */
    val chipMissed: String
    val chipLive: String
    /** Shown when a topic or Live has nothing in it. */
    val nothingHere: String

    // --- watch -------------------------------------------------------------
    val couldNotPlay: String
    val upcomingTitle: String
    val upcomingDetail: String
    val unavailableTitle: String

    /**
     * Why YouTube refused, in words rather than in a wire token.
     *
     * The reason arrives as `members_only`, `private`, `removed` or something
     * else, and it was being printed raw under the title — a viewer reading
     * "members_only" learns that something went wrong and nothing about what.
     * These are the web app's own four sentences, carried across rather than
     * rewritten, so a household that has read the explanation once on a laptop
     * meets the same explanation on a phone.
     */
    val unavailableMembersOnly: String
    val unavailablePrivate: String
    val unavailableRemoved: String
    val unavailableGeneric: String

    /** Over the picture while the first bytes are still arriving. */
    val loadingVideo: String

    /** The two levels and the voice, in the player's settings sheet. */
    val voiceLevel: String
    val videoLevelWhileSpeaking: String
    val voiceName: String
    val voiceNameHint: String
    val play: String
    val pause: String
    val back: String
    /** On the double-tap ripple. Takes the total, which grows as taps repeat. */
    fun seconds(count: Int): String
    val skipBack: String
    val skipForward: String
    val like: String
    val dislike: String
    val saveVideo: String
    val savedVideo: String

    /**
     * Taking a video off the shelf, said on the shelf itself.
     *
     * The same action as [saveVideo] and a different word, because the row it is
     * on is a different claim: in a feed the menu offers to keep something, and
     * on the saved shelf every row is already kept — so an item reading "Saved"
     * there states what is already true and gives no verb to press. Reported
     * from the phone.
     */
    val removeFromSaved: String
    val subscribe: String
    val subscribed: String
    val upNext: String
    val close: String

    // --- channel & search --------------------------------------------------
    /** "videos", after a count. */
    val videos: String
    val searchHint: String
    val noResults: String
    val noResultsDetail: String
    val searchPrompt: String
    /** The heading over results that are already on this disk. */
    val inLibrary: String
    /** The heading over results that are still on YouTube. */
    val onYouTube: String
    /** Said under that heading when the upstream search itself failed. */
    val youtubeUnreachable: String
    /** Said under that heading when YouTube has nothing more to give. */
    val noMoreResults: String

    // --- narration ---------------------------------------------------------
    val narration: String
    /** "Preparing 27/242" — the count is filled in by the caller. */
    fun narrationPreparing(done: Int, total: Int): String
    val narrationFailed: String

    /** Over a broadcast, in place of the two timestamps. */
    val live: String

    /**
     * The same pill once the viewer has rewound inside the broadcast's window.
     *
     * A label rather than a second control: the pill *is* the way back to the
     * edge, and drawing a separate button beside it would be two things saying
     * one thing.
     */
    val goToLive: String
    val fullscreen: String
    val exitFullscreen: String
    val notInterested: String
    val markWatched: String
    /** The gear on the control bar: what belongs to this video. */
    val settingsInPlayer: String
    val subtitles: String
    val off: String
    val autoplay: String
    val comments: String
    val noComments: String
    val subscribersShort: String
    val share: String
    val showMore: String
    val showLess: String
    /** "29 Aug 2026" / "29 thg 8, 2026" — the month is spelled, never numbered. */
    fun dateFormat(day: String, month: Int, year: String): String
    /** "Next: <title>" over the up-next rail. */
    fun nextUp(title: String): String
    val nothingQueued: String
    val allSources: String
    fun fromChannel(name: String): String
    val newBadge: String
    val playNext: String
    val playPrevious: String
    /** The heading over the household's members, on the avatar's sheet. */
    val profileTitle: String

    /** Marks the member this device is currently watching as. */
    val profileCurrent: String

    val savedTitle: String
    val noSaved: String
    val noSavedDetail: String

    // --- playlists ---------------------------------------------------------
    /** The Settings row, and the title of the screen behind it. */
    val playlists: String

    /**
     * The menu item on a card, and the title of the sheet it opens.
     *
     * "Save to playlist" rather than "Save", because the press no longer writes
     * one bit — it opens a question about which collections this belongs in.
     */
    val saveToPlaylist: String
    val newPlaylist: String
    val playlistName: String
    val createPlaylist: String
    val savePlaylist: String

    /** Taking a video off *this* playlist page, said on the page itself. */
    val removeFromPlaylist: String
    val renamePlaylist: String
    val deletePlaylist: String
    val deletePlaylistConfirm: String
    val noPlaylists: String
    val noPlaylistsDetail: String
    val emptyPlaylist: String
    val emptyPlaylistDetail: String
    val playAll: String

    /** Play the collection in a random order. */
    val shufflePlay: String
    val cancel: String

    /** "videos", after a count, on a playlist card. */
    fun playlistCount(count: Int): String
    val feedMix: String
    val feedMixSubscribed: String
    val feedMixAffinity: String
    val feedMixDiscovery: String
    val feedMixHint: String

    // --- navigation --------------------------------------------------------
    val navHome: String
    val navSettings: String
    val search: String

    // --- subscriptions -----------------------------------------------------
    val subscriptionsTitle: String

    /** What the Settings row for subscriptions says under its name. */
    val subscriptionsDetail: String
    /** Shown when this member follows nothing yet. */
    val noSubscriptions: String
    val noSubscriptionsDetail: String
    /** "subscribers", after a count. */
    val subscribers: String

    // --- history -----------------------------------------------------------
    val historyTitle: String
    /** What the History row in Settings says under its name. */
    val historyDetail: String
    val noHistory: String
    val noHistoryDetail: String

    // --- settings ----------------------------------------------------------
    val settingsServer: String
    /** The Profile row in Settings, which used to be the avatar in the bar. */
    val settingsProfile: String
    val settingsLanguage: String
    /** The name of each language, always written in that language. */
    val languageEnglish: String
    val languageVietnamese: String

    // --- units -------------------------------------------------------------
    /** "views", after a count. */
    val views: String

    /**
     * Relative time, as two shapes rather than one shape with a substituted
     * word.
     *
     * English marks the past *before* the unit — "3 days ago" — and Vietnamese
     * *after* the phrase — "3 ngày trước". A single template with a translated
     * word produces the thing that makes a translation read as a machine's. The
     * web app records the same conclusion, and the plural is why: it appended an
     * "s" and printed "3 ngàys trước" in a language with no plural.
     */
    fun relative(value: Int, unit: TimeUnit): String
    val justNow: String

    /**
     * The scale suffixes, thousand / million / billion.
     *
     * A property rather than a hard-coded table in the formatter, because this
     * is exactly where the web app's version went wrong: its formatters carried
     * English *grammar*, appending an "s" in a language that has no plural, and
     * produced "3 ngàys trước" on every card. The unit belongs to the language.
     */
    val thousandSuffix: String
    val millionSuffix: String
    val billionSuffix: String
}

/** The units a relative time is expressed in, largest first. */
enum class TimeUnit(val seconds: Long) {
    Year(31_536_000),
    Month(2_592_000),
    Week(604_800),
    Day(86_400),
    Hour(3_600),
    Minute(60),
}

object EnglishStrings : Strings {
    override val appName = "Mytube"
    override val setupSubtitle = "The address of the Mac at home, on this wifi."
    override val serverAddressLabel = "Server address"
    override val serverAddressHint = "http:// is added for you if you leave it out."
    override val checkUntested = serverAddressHint
    override val checkChecking = "Looking for the library…"
    override val checkReachable = "Found it."
    override val checkFailed = "Nothing answered there. Check the address, and that the Mac is awake."
    override val check = "Check"
    override val save = "Save"

    override val noServerTitle = "No server yet"
    override val setTheAddress = "Set the address"
    override val couldNotReach = "Could not reach the library"
    override val tryAgain = "Try again"
    override val moreOptions = "More"
    override val continueWatching = "Continue watching"
    override val chipAll = "All"
    override val chipMissed = "Missed"
    override val chipLive = "Live"
    override val nothingHere = "Nothing here"

    override val couldNotPlay = "Could not play this"
    override val upcomingTitle = "Not started yet"
    override val upcomingDetail = "It will begin playing on its own."
    override val unavailableTitle = "YouTube will not hand this over"
    override val unavailableMembersOnly =
        "This video is members-only on YouTube. Join the channel there to watch " +
            "it — it cannot be fetched into the library."
    override val unavailablePrivate =
        "This video is private on YouTube, so it cannot be fetched."
    override val unavailableRemoved =
        "This video has been removed from YouTube, so it cannot be fetched."
    override val unavailableGeneric =
        "YouTube will not hand this video over, so it cannot be fetched."
    override val loadingVideo = "Loading video…"
    override val voiceLevel = "Voice volume"
    override val videoLevelWhileSpeaking = "Video volume while speaking"
    override val voiceName = "Voice"
    override val voiceNameHint = "The name your speech service uses"
    override val play = "Play"
    override val pause = "Pause"
    override val back = "Back"
    override fun seconds(count: Int) = "$count seconds"
    override val skipBack = "Back 10 seconds"
    override val skipForward = "Forward 10 seconds"
    override val like = "Like"
    override val dislike = "Dislike"
    override val saveVideo = "Save"
    override val savedVideo = "Saved"
    override val removeFromSaved = "Remove"
    override val subscribe = "Subscribe"
    override val subscribed = "Subscribed"
    override val upNext = "Up next"
    override val close = "Close"

    override val videos = "videos"
    override val searchHint = "Search this library"
    override val noResults = "Nothing here matches"
    override val noResultsDetail = "Try fewer words, or the name of the channel."
    override val searchPrompt = "Titles and channels in this library."
    override val inLibrary = "In your library"
    override val onYouTube = "On YouTube"
    override val youtubeUnreachable = "Could not reach YouTube"
    override val noMoreResults = "Nothing more on YouTube"

    override val narration = "Vietnamese voice"
    override fun narrationPreparing(done: Int, total: Int) = "Preparing $done/$total"
    override val narrationFailed = "The voice could not be prepared"

    // Kept in English in both dictionaries: it is what every player and every
    // television calls a broadcast, the same reasoning as the Live chip.
    override val live = "LIVE"
    override val goToLive = "Go live"
    override val fullscreen = "Fullscreen"
    override val exitFullscreen = "Exit fullscreen"
    override val notInterested = "Not interested"
    override val markWatched = "Watched"
    override val settingsInPlayer = "Settings"
    override val subtitles = "Subtitles"
    override val off = "Off"
    override val autoplay = "Autoplay"
    override val comments = "Comments"
    override val noComments = "No comments yet"
    override val subscribersShort = "subscribers"
    override val share = "Share"
    override val showMore = "...more"
    override val showLess = "Show less"
    override fun dateFormat(day: String, month: Int, year: String): String {
        val months = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        return "$day ${months.getOrElse(month - 1) { "" }} $year"
    }
    override fun nextUp(title: String) = "Next: $title"
    override val nothingQueued = "Nothing queued"
    override val allSources = "All"
    override fun fromChannel(name: String) = "From $name"
    override val newBadge = "New"
    override val playNext = "Next video"
    override val playPrevious = "Previous video"
    override val profileTitle = "Who is watching?"
    override val profileCurrent = "This device"
    override val savedTitle = "Saved"
    override val playlists = "Playlists"
    override val saveToPlaylist = "Save to playlist"
    override val newPlaylist = "New playlist"
    override val playlistName = "Playlist name"
    override val createPlaylist = "Create"
    override val savePlaylist = "Save"
    override val removeFromPlaylist = "Remove from playlist"
    override val renamePlaylist = "Rename"
    override val deletePlaylist = "Delete playlist"
    override val deletePlaylistConfirm = "Delete this playlist? The videos stay in the library."
    override val noPlaylists = "No playlists yet"
    override val noPlaylistsDetail =
        "The menu on any video offers to save it to a playlist, and a new one can be named there."
    override val emptyPlaylist = "Nothing in here yet"
    override val emptyPlaylistDetail =
        "Videos saved to this playlist appear here, in the order they were added."
    override val playAll = "Play all"
    override val shufflePlay = "Shuffle"
    override val cancel = "Cancel"
    override fun playlistCount(count: Int) = if (count == 1) "1 video" else "$count videos"
    override val noSaved = "Nothing saved yet"
    override val noSavedDetail =
        "Saving a video keeps its file on the disk when the library runs out of room."
    override val feedMix = "Home feed"
    override val feedMixSubscribed = "Channels you follow"
    override val feedMixAffinity = "More of what you watch"
    override val feedMixDiscovery = "Something new"
    override val feedMixHint =
        "These three divide what is left after continue watching, rewatch and new uploads."

    override val navHome = "Home"
    override val navSettings = "Settings"
    override val search = "Search"

    override val subscriptionsTitle = "Subscriptions"
    override val subscriptionsDetail = "The channels this household follows"
    override val noSubscriptions = "You do not follow any channels yet"
    override val noSubscriptionsDetail =
        "Channels you subscribe to on YouTube arrive here on the next account scan."
    override val subscribers = "subscribers"

    override val historyTitle = "Watch history"
    override val historyDetail = "Everything this member has played, newest first."
    override val noHistory = "Nothing watched yet"
    override val noHistoryDetail = "Videos you play show up here, most recent first."

    override val settingsServer = "Server address"
    override val settingsProfile = "Profile"
    override val settingsLanguage = "Language"
    override val languageEnglish = "English"
    override val languageVietnamese = "Tiếng Việt"

    override val views = "views"

    override fun relative(value: Int, unit: TimeUnit): String {
        val word = when (unit) {
            TimeUnit.Year -> "year"
            TimeUnit.Month -> "month"
            TimeUnit.Week -> "week"
            TimeUnit.Day -> "day"
            TimeUnit.Hour -> "hour"
            TimeUnit.Minute -> "minute"
        }
        return "$value $word${if (value > 1) "s" else ""} ago"
    }

    override val justNow = "just now"
    override val thousandSuffix = "K"
    override val millionSuffix = "M"
    override val billionSuffix = "B"
}

object VietnameseStrings : Strings {
    override val appName = "Mytube"
    override val setupSubtitle = "Địa chỉ của máy Mac ở nhà, trong cùng wifi này."
    override val serverAddressLabel = "Địa chỉ máy chủ"
    override val serverAddressHint = "Không gõ http:// cũng được, tự thêm."
    override val checkUntested = serverAddressHint
    override val checkChecking = "Đang tìm thư viện…"
    override val checkReachable = "Thấy rồi."
    override val checkFailed = "Không có gì trả lời. Xem lại địa chỉ, và xem máy Mac đã bật chưa."
    override val check = "Kiểm tra"
    override val save = "Lưu"

    override val noServerTitle = "Chưa có máy chủ"
    override val setTheAddress = "Nhập địa chỉ"
    override val couldNotReach = "Không kết nối được tới thư viện"
    override val tryAgain = "Thử lại"
    override val moreOptions = "Thêm"
    override val continueWatching = "Xem tiếp"
    override val chipAll = "Tất cả"
    override val chipMissed = "Bỏ lỡ"
    // Kept in English: it is what every player and every television calls a
    // broadcast, and "trực tiếp" is longer than the chip it sits in.
    override val chipLive = "Live"
    override val nothingHere = "Chưa có gì ở đây"

    override val couldNotPlay = "Không phát được"
    override val upcomingTitle = "Chưa bắt đầu"
    override val upcomingDetail = "Tới giờ nó sẽ tự phát."
    override val unavailableTitle = "YouTube không cho lấy video này"
    override val unavailableMembersOnly =
        "Video này chỉ dành cho thành viên trên YouTube. Muốn xem thì tham gia " +
            "kênh bên đó — không tải về thư viện được."
    override val unavailablePrivate = "Video này để riêng tư trên YouTube nên không tải được."
    override val unavailableRemoved = "Video này đã bị gỡ khỏi YouTube nên không tải được."
    override val unavailableGeneric = "YouTube không cho lấy video này nên không tải được."
    override val loadingVideo = "Đang tải video…"
    override val voiceLevel = "Âm lượng giọng đọc"
    override val videoLevelWhileSpeaking = "Âm lượng video khi đang đọc"
    override val voiceName = "Giọng đọc"
    override val voiceNameHint = "Tên giọng mà dịch vụ đọc của bạn dùng"
    override val play = "Phát"
    override val pause = "Tạm dừng"
    override val back = "Quay lại"
    override fun seconds(count: Int) = "$count giây"
    override val skipBack = "Lùi 10 giây"
    override val skipForward = "Tới 10 giây"
    override val like = "Thích"
    override val dislike = "Không thích"
    override val saveVideo = "Lưu"
    override val savedVideo = "Đã lưu"
    override val removeFromSaved = "Bỏ lưu"
    override val subscribe = "Đăng ký"
    override val subscribed = "Đã đăng ký"
    override val upNext = "Xem tiếp"
    override val close = "Đóng"

    override val videos = "video"
    override val searchHint = "Tìm trong thư viện"
    override val noResults = "Không có gì khớp"
    override val noResultsDetail = "Thử ít chữ hơn, hoặc gõ tên kênh."
    override val searchPrompt = "Tìm theo tên video và tên kênh trong thư viện."
    override val inLibrary = "Trong thư viện"
    override val onYouTube = "Trên YouTube"
    override val youtubeUnreachable = "Không tới được YouTube"
    override val noMoreResults = "Hết kết quả trên YouTube"

    override val narration = "Thuyết minh"
    override fun narrationPreparing(done: Int, total: Int) = "Đang chuẩn bị $done/$total"
    override val narrationFailed = "Không chuẩn bị được giọng đọc"

    override val live = "LIVE"
    override val goToLive = "Xem trực tiếp"
    override val fullscreen = "Toàn màn hình"
    override val exitFullscreen = "Thoát toàn màn hình"
    override val notInterested = "Không quan tâm"
    override val markWatched = "Đã xem"
    override val settingsInPlayer = "Cài đặt"
    override val subtitles = "Phụ đề"
    override val off = "Tắt"
    override val autoplay = "Tự động phát"
    override val comments = "Bình luận"
    override val noComments = "Chưa có bình luận"
    override val subscribersShort = "người đăng ký"
    override val share = "Chia sẻ"
    override val showMore = "...thêm"
    override val showLess = "Thu gọn"
    // "29 thg 8, 2026" — the form ICU gives for vi-VN, and the one the web app
    // settled on rather than a table of month names.
    override fun dateFormat(day: String, month: Int, year: String) = "$day thg $month, $year"
    override fun nextUp(title: String) = "Tiếp theo: $title"
    override val nothingQueued = "Chưa có gì tiếp theo"
    override val allSources = "Tất cả"
    override fun fromChannel(name: String) = "Từ $name"
    override val newBadge = "Mới"
    override val playNext = "Video kế tiếp"
    override val playPrevious = "Video trước"
    override val profileTitle = "Ai đang xem?"
    override val profileCurrent = "Thiết bị này"
    override val savedTitle = "Đã lưu"
    override val playlists = "Playlist"
    override val saveToPlaylist = "Lưu vào playlist"
    override val newPlaylist = "Playlist mới"
    override val playlistName = "Tên playlist"
    override val createPlaylist = "Tạo"
    override val savePlaylist = "Lưu"
    override val removeFromPlaylist = "Bỏ khỏi playlist"
    override val renamePlaylist = "Đổi tên"
    override val deletePlaylist = "Xoá playlist"
    override val deletePlaylistConfirm = "Xoá playlist này? Video vẫn còn trong thư viện."
    override val noPlaylists = "Chưa có playlist nào"
    override val noPlaylistsDetail =
        "Menu trên mỗi video có mục lưu vào playlist, và có thể đặt tên playlist mới ngay ở đó."
    override val emptyPlaylist = "Playlist này còn trống"
    override val emptyPlaylistDetail =
        "Video lưu vào playlist này sẽ hiện ở đây, theo thứ tự đã thêm."
    override val playAll = "Phát tất cả"
    override val shufflePlay = "Phát ngẫu nhiên"
    override val cancel = "Huỷ"
    // Vietnamese has no plural form, so one string covers both — the web app's
    // "3 ngàys trước" is what happens when a suffix travels between languages.
    override fun playlistCount(count: Int) = "$count video"
    override val noSaved = "Chưa lưu video nào"
    override val noSavedDetail = "Lưu một video là giữ file của nó lại khi ổ đĩa đầy."
    override val feedMix = "Trang chủ"
    override val feedMixSubscribed = "Kênh đang theo dõi"
    override val feedMixAffinity = "Giống thứ bạn hay xem"
    override val feedMixDiscovery = "Thử cái mới"
    override val feedMixHint =
        "Ba phần này chia nhau chỗ còn lại, sau xem tiếp, xem lại và video mới."

    override val navHome = "Trang chủ"
    override val navSettings = "Cài đặt"
    override val search = "Tìm kiếm"

    override val subscriptionsTitle = "Kênh đăng ký"
    override val subscriptionsDetail = "Những kênh nhà này theo dõi"
    override val noSubscriptions = "Chưa theo dõi kênh nào"
    override val noSubscriptionsDetail =
        "Kênh bạn đăng ký trên YouTube sẽ về đây ở lần quét tài khoản kế tiếp."
    override val subscribers = "người đăng ký"

    override val historyTitle = "Đã xem"
    override val historyDetail = "Mọi video người này đã mở, mới nhất lên trước."
    override val noHistory = "Chưa xem gì"
    override val noHistoryDetail = "Video bạn mở sẽ nằm ở đây, mới nhất lên trước."

    override val settingsServer = "Địa chỉ máy chủ"
    override val settingsProfile = "Người xem"
    override val settingsLanguage = "Ngôn ngữ"
    // Each language is named in its own words, always. Somebody who pressed the
    // wrong row is looking at an interface they cannot read, and "English"
    // written in English is the way back out.
    override val languageEnglish = "English"
    override val languageVietnamese = "Tiếng Việt"

    override val views = "lượt xem"

    override fun relative(value: Int, unit: TimeUnit): String {
        // No plural form of any of them, and the marker goes last.
        val word = when (unit) {
            TimeUnit.Year -> "năm"
            TimeUnit.Month -> "tháng"
            TimeUnit.Week -> "tuần"
            TimeUnit.Day -> "ngày"
            TimeUnit.Hour -> "giờ"
            TimeUnit.Minute -> "phút"
        }
        return "$value $word trước"
    }

    override val justNow = "vừa xong"

    // N / Tr / T — nghìn, triệu, tỷ. Taken from the web app's own table rather
    // than invented, so both clients abbreviate a view count the same way.
    override val thousandSuffix = "N"
    override val millionSuffix = "Tr"
    override val billionSuffix = "T"
}

enum class Language(val code: String) {
    English("en"),
    Vietnamese("vi"),
    ;

    val strings: Strings
        get() = when (this) {
            English -> EnglishStrings
            Vietnamese -> VietnameseStrings
        }

    companion object {
        /**
         * What the device is set to, or English.
         *
         * English for a machine nobody has set up, matching the web app. The tag
         * may be `vi-VN`, so only the primary subtag is compared.
         */
        fun forTag(tag: String): Language =
            if (tag.substringBefore('-').lowercase() == "vi") Vietnamese else English
    }
}

/**
 * The strings in scope.
 *
 * `compositionLocalOf` and not `staticCompositionLocalOf`: the value changes
 * when somebody switches language, and the static variant does not recompose
 * readers when it does.
 */
val LocalStrings = compositionLocalOf<Strings> { EnglishStrings }
