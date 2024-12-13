package spam.blocker.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.service.checker.Checker
import spam.blocker.util.ContactInfo
import spam.blocker.util.Contacts
import spam.blocker.util.Now
import spam.blocker.util.Permissions
import spam.blocker.util.PhoneNumber
import spam.blocker.util.Util
import spam.blocker.util.spf
import spam.blocker.util.spf.RecentAppInfo
import java.util.Calendar


class RuleTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    val A = "111" // Allow
    val B = "222" // Block

    // This is executed before every test case runs
    @Before
    fun setup() {
        clearAllMocks()

        spf.SharedPref(ctx).clear()
        NumberRuleTable().clearAll(ctx)
        ContentRuleTable().clearAll(ctx)
    }

    @After
    fun teardown() {
    }

    fun build_rule(
        pattern: String,
        patternExtra: String,
        priority: Int,
        isBlacklist: Boolean,
        flagCallSms: Int,
        patternFlags: Int = 0,
        patternExtraFlags: Int = 0,
        schedule: String = ""
    ): RegexRule {
        val f = RegexRule()
        f.pattern = pattern
        f.patternExtra = patternExtra
        f.priority = priority
        f.isBlacklist = isBlacklist
        f.flags = flagCallSms
        f.patternFlags = patternFlags
        f.patternExtraFlags = patternExtraFlags
        f.schedule = schedule

        return f
    }

    fun add_number_rule(f: RegexRule) {
        NumberRuleTable().addNewRule(ctx, f)
    }

    fun add_content_rule(f: RegexRule) {
        ContentRuleTable().addNewRule(ctx, f)
    }


    private fun mock_contact(rawNumber: String) {
        val ci = ContactInfo(
            id = "",
            name = "Mock_contact_$rawNumber"
        )

        mockkObject(Permissions)
        every { Permissions.isContactsPermissionGranted(ctx) } returns true

        mockkObject(Contacts)
        every { Contacts.findContactByRawNumber(ctx, any()) } answers {
            val num = secondArg<String>()
            if (Util.clearNumber(rawNumber).endsWith(num))
                ci
            else
                null
        }
    }

    private fun mock_call_permission_granted() {
        every { Permissions.isCallLogPermissionGranted(any()) } returns true
    }

    private fun mock_sms_permission_granted() {
        every { Permissions.isReadSmsPermissionGranted(any()) } returns true
    }

    private fun mock_calls(
        rawNumber: String,
        direction: Int,
        repeatedTimes: Int,
        atTimeMillis: Long
    ) {
        val number = PhoneNumber(ctx, rawNumber)
        every { Permissions.getHistoryCallsByNumber(any(), number, direction, any()) } answers {
            val withinMillis = lastArg<Long>()
            val mockNow = Now.currentMillis()
            if (atTimeMillis in mockNow - withinMillis..mockNow) {
                List<Int>(repeatedTimes) {0}
            } else {
                listOf()
            }
        }
    }

    private fun mock_sms(
        rawNumber: String,
        direction: Int,
        repeatedTimes: Int,
        atTimeMillis: Long
    ) {
        val number = PhoneNumber(ctx, rawNumber)
        every { Permissions.countHistorySMSByNumber(any(), number, direction, any()) } answers {
            val withinMillis = lastArg<Long>()
            val mockNow = Now.currentMillis()
            if (atTimeMillis in mockNow - withinMillis..mockNow) {
                repeatedTimes
            } else {
                0
            }
        }
    }

    private fun mock_advance_time_by_minutes(durationMinutes: Int) {
        mockkObject(Now)
        every { Now.currentMillis() } answers { System.currentTimeMillis() + durationMinutes * 60 * 1000 }
    }

    private fun mock_current_time_millis(millis: Long) {
        mockkObject(Now)
        every { Now.currentMillis() } returns millis
    }

    // -------- tests begin --------

    // In Contact > blacklist
    @Test
    fun contact_inclusive() {
        val spf = spf.Contact(ctx)
        spf.setEnabled(true)
        spf.setExclusive(false)
        mock_contact(A)
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // contact: pass
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, Checker.Companion.checkCall(ctx, logger = null, A).type)

        // non-contact: block
        assertEquals(Def.RESULT_BLOCKED_BY_NUMBER, Checker.Companion.checkCall(ctx, logger = null, B).type)
    }

    // Non Contact -> block
    @Test
    fun contact_exclusive() {
        val spf = spf.Contact(ctx)
        spf.setEnabled(true)
        spf.setExclusive(true)
        mock_contact(A)

        // contact: pass
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, Checker.Companion.checkCall(ctx, logger = null, A).type)

        // non-contact: block
        assertEquals(
            Def.RESULT_BLOCKED_BY_NON_CONTACT,
            Checker.Companion.checkCall(ctx, logger = null, B).type
        )
    }

    // testing repeated call
    @Test
    fun repeated_call() {
        spf.RepeatedCall(ctx).apply {
            setEnabled(true)
            setTimes(4)
            setInXMin(5)
        }

        mockkObject(Permissions)
        mock_call_permission_granted()
        mock_sms_permission_granted()

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // mock no call/sms record
        val now = System.currentTimeMillis()
        val twoMinAgo = now - 2 * 60 * 1000
        mock_calls(B, Def.DIRECTION_INCOMING, 0, twoMinAgo)
        mock_sms(B, Def.DIRECTION_INCOMING, 0, twoMinAgo)

        // should always fail when no history records
        for (i in 1..3) {
            val r = Checker.Companion.checkCall(ctx, logger = null, B)
            assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)
        }

        // add two calls
        mock_calls(B, Def.DIRECTION_INCOMING, 2, twoMinAgo)

        // should still fail, 2<4
        val r2 = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r2.type)

        // add two sms
        mock_sms(B, Def.DIRECTION_INCOMING, 2, twoMinAgo)
        // should pass, 2+2>=4
        val r3 = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_REPEATED, r3.type)

        // should block again after 10 minutes
        mock_advance_time_by_minutes(10)
        val r4 = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r4.type)
    }

    // testing dialed
    @Test
    fun dialed() {
        val spf = spf.Dialed(ctx)
        val inXdays = 5
        spf.setEnabled(true)
        spf.setDays(inXdays)

        mockkObject(Permissions)
        mock_call_permission_granted()
        mock_sms_permission_granted()

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // mock no call/sms record
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2 * 24 * 3600 * 1000
        mock_calls(B, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)
        mock_sms(B, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)

        // should always fail when no history records
        for (i in 1..3) {
            val r = Checker.Companion.checkCall(ctx, logger = null, B)
            assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)
        }

        // add a call
        mock_calls(B, Def.DIRECTION_OUTGOING, 1, twoDaysAgo)

        // should pass
        val r1 = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DIALED, r1.type)


        // clear that call, add an sms
        mock_calls(B, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)
        mock_sms(B, Def.DIRECTION_OUTGOING, 1, twoDaysAgo)

        // should pass
        val r2 = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DIALED, r2.type)

        // should block again after 10 days
        mock_advance_time_by_minutes(10 * 24 * 60)
        val r4 = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r4.type)
    }

    private fun mock_recent_app(pkgs: List<String>, expire: Long) {
        mockkObject(Permissions)
        every { Permissions.listUsedAppWithinXSecond(ctx, any()) } answers {
            if (Now.currentMillis() < expire)
                pkgs
            else
                listOf()
        }
    }

    @Test
    fun recent_app() {
        val spf = spf.RecentApps(ctx)
        val pkgs = listOf("my.pkg")
        spf.setDefaultMin(5)
        spf.setList(pkgs.map { RecentAppInfo(it) })

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // app being used
        mock_recent_app(pkgs, System.currentTimeMillis() + 5 * 60 * 1000)

        // should pass
        var r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_RECENT_APP, r.type)

        // 5 min expired
        mock_advance_time_by_minutes(6)

        // should block
        r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)

    }

    fun mock_current_hour_min(hour: Int, min: Int) {
        mockkObject(Util)

        every { Util.currentHourMin() } returns Pair(hour, min)
    }

    @Test
    fun off_time() {
        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // should block
        var r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)

        // set off-time to (1:00, 2:00)
        val spf = spf.OffTime(ctx).apply {
            setEnabled(true)

            setStartHour(1)
            setStartMin(0)
            setEndHour(2)
            setEndMin(0)
        }

        // mock current time to 1:15
        mock_current_hour_min(1, 15)

        // should pass
        r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_OFF_TIME, r.type)

        // mock current time to 3:00
        mock_current_hour_min(3, 0)

        // should block
        r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)


        // ---- test start > end (over night) ----
        spf.setStartHour(21)
        spf.setStartMin(0) // 21 PM -> 02 AM
        spf.setEndHour(2)
        spf.setEndMin(0)
        // mock current time to 22:00
        mock_current_hour_min(22, 0)
        // should pass
        r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_OFF_TIME, r.type)
    }

    private fun getTimestampInMilliseconds(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // rule schedule
    @Test
    fun rule_schedule() {
        // block all number
        add_number_rule(
            build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL,
                schedule = "2,01:00-02:00,true")
        )

        // should block, Monday 01:20
        mock_current_time_millis(getTimestampInMilliseconds(2, 1, 20))
        var r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)

        // should pass, Thursday
        mock_current_time_millis(getTimestampInMilliseconds(3, 1, 20))
        r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

    }

    // testing call number rules
    @Test
    fun number_rules_priority() {
        // block all number, priority 1
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))
        // allow A, priority: 2
        add_number_rule(build_rule(A, "", 2, false, Def.FLAG_FOR_CALL))

        // set B as Contact
        mock_contact(B)
        // block B, priority: 100
        add_number_rule(build_rule("$B.*", "", 100, true, Def.FLAG_FOR_CALL))

        // A should pass
        var r = Checker.Companion.checkCall(ctx, logger = null, A)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_NUMBER, r.type)

        // B should block
        r = Checker.Companion.checkCall(ctx, logger = null, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)
    }

    // test incoming number like "Microsoft"
    @Test
    fun from_microsoft() {
        val Microsoft = "Microsoft"

        // block Microsoft
        add_number_rule(build_rule(Microsoft, "", 1, true, Def.FLAG_FOR_CALL))

        //  should block
        val r = Checker.Companion.checkCall(ctx, logger = null, Microsoft)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)
    }

    // block all msg contain "discount"
    // but pass for particular number even it contains "discount"
    @Test
    fun for_particular_number() {
        val particularNumber = "123"

        val msg = " discount "

        add_content_rule(build_rule(".*discount.*", "", 1, true, Def.FLAG_FOR_SMS))
        add_content_rule(build_rule(".*discount.*", particularNumber, 2, false, Def.FLAG_FOR_SMS))

        // should pass for particular number
        var r = Checker.Companion.checkSms(ctx, logger = null, particularNumber, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_CONTENT, r.type)

        //  should block for anyone else
        r = Checker.Companion.checkSms(ctx, logger = null, A, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.type)
    }

    // test regex flags IgnoreCase
    @Test
    fun regex_flags_ignore_case() {
        val msg = " Discount "

        // should pass without this flag
        add_content_rule(build_rule(".*discount.*", "", 1, true, Def.FLAG_FOR_SMS))
        var r = Checker.Companion.checkSms(ctx, logger = null, B, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        // should block with this flag set
        add_content_rule(
            build_rule(
                ".*discount.*", "", 1, true, Def.FLAG_FOR_SMS,
                patternFlags = Def.FLAG_REGEX_IGNORE_CASE
            )
        )

        r = Checker.Companion.checkSms(ctx, logger = null, B, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.type)
    }

    // test regex flags DotMatchAll
    @Test
    fun regex_flags_dot_match_all() {
        val msg = " http://\nabc.com "
        val rule = ".*http.*com.*"

        // should pass without this flag
        add_content_rule(build_rule(rule, "", 1, true, Def.FLAG_FOR_SMS))
        var r = Checker.Companion.checkSms(ctx, logger = null, B, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        // should block with this flag set
        add_content_rule(
            build_rule(
                rule, "", 1, true, Def.FLAG_FOR_SMS,
                patternFlags = Def.FLAG_REGEX_DOT_MATCH_ALL
            )
        )

        r = Checker.Companion.checkSms(ctx, logger = null, B, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.type)
    }

    // regex flags RawNumber
    @Test
    fun regex_flags_raw_number() {
        val domesticNumber = "00123"

        add_number_rule(
            build_rule("0.*", "", 2, true, Def.FLAG_FOR_CALL,
                patternFlags = Def.FLAG_REGEX_RAW_NUMBER)
        )

        // should pass by default
        var r = Checker.Companion.checkCall(ctx, logger = null, A)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        //  should block for leading 0
        r = Checker.Companion.checkCall(ctx, logger = null, domesticNumber)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.type)
    }
}