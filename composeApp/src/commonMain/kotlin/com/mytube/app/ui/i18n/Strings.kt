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
    val chipLive: String
    /** Shown when a topic or Live has nothing in it. */
    val nothingHere: String

    // --- watch -------------------------------------------------------------
    val couldNotPlay: String
    val upcomingTitle: String
    val upcomingDetail: String
    val unavailableTitle: String
    val play: String
    val pause: String
    val back: String
    val skipBack: String
    val skipForward: String
    val like: String
    val dislike: String
    val saveVideo: String
    val savedVideo: String
    val subscribe: String
    val subscribed: String
    val upNext: String
    val close: String

    // --- navigation --------------------------------------------------------
    val navHome: String
    val navSubscriptions: String
    val navHistory: String
    val navSettings: String
    val search: String

    // --- subscriptions -----------------------------------------------------
    val subscriptionsTitle: String
    /** Shown when this member follows nothing yet. */
    val noSubscriptions: String
    val noSubscriptionsDetail: String
    /** "subscribers", after a count. */
    val subscribers: String

    // --- history -----------------------------------------------------------
    val historyTitle: String
    val noHistory: String
    val noHistoryDetail: String

    // --- settings ----------------------------------------------------------
    val settingsServer: String
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
    override val chipLive = "Live"
    override val nothingHere = "Nothing here"

    override val couldNotPlay = "Could not play this"
    override val upcomingTitle = "Not started yet"
    override val upcomingDetail = "It will begin playing on its own."
    override val unavailableTitle = "YouTube will not hand this over"
    override val play = "Play"
    override val pause = "Pause"
    override val back = "Back"
    override val skipBack = "Back 10 seconds"
    override val skipForward = "Forward 10 seconds"
    override val like = "Like"
    override val dislike = "Dislike"
    override val saveVideo = "Save"
    override val savedVideo = "Saved"
    override val subscribe = "Subscribe"
    override val subscribed = "Subscribed"
    override val upNext = "Up next"
    override val close = "Close"

    override val navHome = "Home"
    override val navSubscriptions = "Subscriptions"
    override val navHistory = "History"
    override val navSettings = "Settings"
    override val search = "Search"

    override val subscriptionsTitle = "Subscriptions"
    override val noSubscriptions = "You do not follow any channels yet"
    override val noSubscriptionsDetail =
        "Channels you subscribe to on YouTube arrive here on the next account scan."
    override val subscribers = "subscribers"

    override val historyTitle = "Watch history"
    override val noHistory = "Nothing watched yet"
    override val noHistoryDetail = "Videos you play show up here, most recent first."

    override val settingsServer = "Server address"
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
    // Kept in English: it is what every player and every television calls a
    // broadcast, and "trực tiếp" is longer than the chip it sits in.
    override val chipLive = "Live"
    override val nothingHere = "Chưa có gì ở đây"

    override val couldNotPlay = "Không phát được"
    override val upcomingTitle = "Chưa bắt đầu"
    override val upcomingDetail = "Tới giờ nó sẽ tự phát."
    override val unavailableTitle = "YouTube không cho lấy video này"
    override val play = "Phát"
    override val pause = "Tạm dừng"
    override val back = "Quay lại"
    override val skipBack = "Lùi 10 giây"
    override val skipForward = "Tới 10 giây"
    override val like = "Thích"
    override val dislike = "Không thích"
    override val saveVideo = "Lưu"
    override val savedVideo = "Đã lưu"
    override val subscribe = "Đăng ký"
    override val subscribed = "Đã đăng ký"
    override val upNext = "Xem tiếp"
    override val close = "Đóng"

    override val navHome = "Trang chủ"
    override val navSubscriptions = "Kênh đăng ký"
    override val navHistory = "Đã xem"
    override val navSettings = "Cài đặt"
    override val search = "Tìm kiếm"

    override val subscriptionsTitle = "Kênh đăng ký"
    override val noSubscriptions = "Chưa theo dõi kênh nào"
    override val noSubscriptionsDetail =
        "Kênh bạn đăng ký trên YouTube sẽ về đây ở lần quét tài khoản kế tiếp."
    override val subscribers = "người đăng ký"

    override val historyTitle = "Đã xem"
    override val noHistory = "Chưa xem gì"
    override val noHistoryDetail = "Video bạn mở sẽ nằm ở đây, mới nhất lên trước."

    override val settingsServer = "Địa chỉ máy chủ"
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
