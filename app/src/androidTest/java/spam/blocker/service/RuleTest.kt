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
import spam.blocker.db.CallTable
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.Flag
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.PatternRule
import spam.blocker.db.Record
import spam.blocker.def.Def
import spam.blocker.util.ContactInfo
import spam.blocker.util.Contacts
import spam.blocker.util.Permission
import spam.blocker.util.SharedPref
import spam.blocker.util.Time
import spam.blocker.util.Util


class RuleTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val spf = SharedPref(ctx)

    val A = "111" // Allow
    val B = "222" // Block

    // This is executed before every test case runs
    @Before
    fun setup() {
        clearAllMocks()

        spf.setContactEnabled(false)
        spf.setContactExclusive(false)
        spf.setRepeatedCallEnabled(false)
        spf.setRepeatedConfig(1, 5)
        spf.setRecentAppList(listOf())
        spf.setRecentAppConfig(5)
        spf.setDialedEnabled(false)
        spf.setDialedConfig(3)
        spf.setOffTimeEnabled(false)
        NumberRuleTable().clearAll(ctx)
        ContentRuleTable().clearAll(ctx)
    }
    @After
    fun teardown() {}

    fun build_rule(
        pattern: String, patternExtra: String, priority: Int, isBlacklist: Boolean, flagCallSms: Int,
        patternFlags: Int = 0, patternExtraFlags: Int = 0
    ) : PatternRule {
        val f = PatternRule()
        f.pattern = pattern
        f.patternExtra = patternExtra
        f.priority = priority
        f.isBlacklist = isBlacklist
        f.flagCallSms = Flag(flagCallSms)
        f.patternFlags = Flag(patternFlags)
        f.patternExtraFlags = Flag(patternExtraFlags)

        return f
    }

    fun add_number_rule(f: PatternRule) {
        NumberRuleTable().addNewPatternRule(ctx, f)
    }
    fun add_content_rule(f: PatternRule) {
        ContentRuleTable().addNewPatternRule(ctx, f)
    }


    private fun mock_contact(rawNumber: String) {
        val ci = ContactInfo()
        ci.name = "Mock_contact_$rawNumber"

        mockkObject(Permission)
        every { Permission.isContactsPermissionGranted(ctx) } returns true

        mockkObject(Contacts)
        every { Contacts.findByRawNumberAuto(ctx, any()) } answers {
            val num = secondArg<String>()
            if (Util.clearNumber(rawNumber).endsWith(num))
                ci
            else
                null
        }
    }

    private fun mock_call_permission_granted() {
        every { Permission.isCallLogPermissionGranted(any()) } returns true
    }
    private fun mock_sms_permission_granted() {
        every { Permission.isReadSmsPermissionGranted(any()) } returns true
    }
    private fun mock_calls(rawNumber: String, direction: Int, repeatedTimes: Int, atTimeMillis: Long) {
        every { Permission.countHistoryCallByNumber(any(), rawNumber, direction, any()) } answers {
            val withinMillis = lastArg<Long>()
            val mockNow = Time.currentTimeMillis()
            if (atTimeMillis in mockNow - withinMillis..mockNow) {
                repeatedTimes
            } else {
                0
            }
        }
    }
    private fun mock_sms(rawNumber: String, direction: Int, repeatedTimes: Int, atTimeMillis: Long) {
        every { Permission.countHistorySMSByNumber(any(), rawNumber, direction, any()) } answers {
            val withinMillis = lastArg<Long>()
            val mockNow = Time.currentTimeMillis()
            if (atTimeMillis in mockNow - withinMillis..mockNow) {
                repeatedTimes
            } else {
                0
            }
        }
    }
    private fun mock_advance_time_by_minutes(durationMinutes: Int) {
        mockkObject(Time)
        every { Time.currentTimeMillis() } answers { System.currentTimeMillis() + durationMinutes*60*1000}
    }

    // -------- tests begin --------

    // In Contact > blacklist
    @Test
    fun contact_inclusive() {
        spf.setContactEnabled(true)
        spf.setContactExclusive(false)
        mock_contact(A)
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // contact: pass
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, Checker.checkCall(ctx, A).result)

        // non-contact: block
        assertEquals(Def.RESULT_BLOCKED_BY_NUMBER, Checker.checkCall(ctx, B).result)
    }

    // Non Contact -> block
    @Test
    fun contact_exclusive() {
        spf.setContactEnabled(true)
        spf.setContactExclusive(true)
        mock_contact(A)

        // contact: pass
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, Checker.checkCall(ctx, A).result)

        // non-contact: block
        assertEquals(Def.RESULT_BLOCKED_BY_NON_CONTACT, Checker.checkCall(ctx, B).result)
    }

    // testing contact contains special characters
//    @Test
//    fun contact_with_special_characters() {
//        val C = "+1 222-333 4444" // Contact with Special Characters
//        val incomingCalls = listOf("2223334444", "12223334444", "+12223334444", "+1 222 333-4444", "1 222 333 4 4 4 4")
//
//        spf.setContactEnabled(true)
//        spf.setContactExclusive(false)
//        mock_contact(C)
//
//        // all match
//        incomingCalls.forEach {
//            assertEquals("should match: <$C> - <$it>", Def.RESULT_ALLOWED_BY_CONTACT, Checker.checkCall(ctx, it).result)
//        }
//        // should block
//        assertEquals("should block <$C> - <$B>", Def.RESULT_ALLOWED_BY_DEFAULT, Checker.checkCall(ctx, B).result)
//    }

    // testing repeated call
    @Test
    fun repeated_call() {
        val inXmin = 5
        spf.setRepeatedCallEnabled(true)
        spf.setRepeatedConfig(4, inXmin)

        mockkObject(Permission)
        mock_call_permission_granted()
        mock_sms_permission_granted()

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // mock no call/sms record
        val now = System.currentTimeMillis()
        val twoMinAgo = now - 2*60*1000
        mock_calls(B, Def.DIRECTION_INCOMING, 0, twoMinAgo)
        mock_sms(B, Def.DIRECTION_INCOMING, 0, twoMinAgo)

        // should always fail when no history records
        for (i in 1..3) {
            val r = Checker.checkCall(ctx, B)
            assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)
        }

        // add two calls
        mock_calls(B, Def.DIRECTION_INCOMING, 2, twoMinAgo)

        // should still fail, 2<4
        val r2 = Checker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r2.result)

        // add two sms
        mock_sms(B, Def.DIRECTION_INCOMING, 2, twoMinAgo)
        // should pass, 2+2>=4
        val r3 = Checker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_REPEATED, r3.result)

        // should block again after 10 minutes
        mock_advance_time_by_minutes(10)
        val r4 = Checker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r4.result)
    }

    // testing dialed
    @Test
    fun dialed() {
        val inXdays = 5
        spf.setDialedEnabled(true)
        spf.setDialedConfig(inXdays)

        mockkObject(Permission)
        mock_call_permission_granted()
        mock_sms_permission_granted()

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // mock no call/sms record
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2*24*3600*1000
        mock_calls(B, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)
        mock_sms(B, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)

        // should always fail when no history records
        for (i in 1..3) {
            val r = Checker.checkCall(ctx, B)
            assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)
        }

        // add a call
        mock_calls(B, Def.DIRECTION_OUTGOING, 1, twoDaysAgo)

        // should pass
        val r1 = Checker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DIALED, r1.result)


        // clear that call, add an sms
        mock_calls(B, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)
        mock_sms(B, Def.DIRECTION_OUTGOING, 1, twoDaysAgo)

        // should pass
        val r2 = Checker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DIALED, r2.result)

        // should block again after 10 days
        mock_advance_time_by_minutes(10*24*60)
        val r4 = Checker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r4.result)
    }
    private fun mock_recent_app(pkgs: List<String>, expire: Long) {
        mockkObject(Permission)
        every { Permission.listUsedAppWithinXSecond(ctx, any()) } answers {
            if (Time.currentTimeMillis() < expire)
                pkgs
            else
                listOf()
        }
    }

    @Test
    fun recent_app() {
        val pkgs = listOf("my.pkg")
        spf.setRecentAppConfig(5)
        spf.setRecentAppList(pkgs)

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // app being used
        mock_recent_app(pkgs, System.currentTimeMillis() + 5*60*1000)

        // should pass
        var r = Checker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_RECENT_APP, r.result)

        // 5 min expired
        mock_advance_time_by_minutes(6)

        // should block
        r = Checker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)

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
        var r = Checker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)

        // set off-time to (1:00, 2:00)
        spf.setOffTimeEnabled(true)
        spf.setOffTimeStart(1, 0)
        spf.setOffTimeEnd(2, 0)

        // mock current time to 1:15
        mock_current_hour_min(1, 15)

        // should pass
        r = Checker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_OFF_TIME, r.result)

        // mock current time to 3:00
        mock_current_hour_min(3, 0)

        // should block
        r = Checker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)


        // ---- test start > end (over night) ----
        spf.setOffTimeStart(21, 0) // 21 PM -> 02 AM
        spf.setOffTimeEnd(2, 0)
        // mock current time to 22:00
        mock_current_hour_min(22, 0)
        // should pass
        r = Checker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_OFF_TIME, r.result)
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
        var r = Checker.checkCall(ctx, A)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_NUMBER, r.result)

        // B should block
        r = Checker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)
    }

    // test incoming number like "Microsoft"
    @Test
    fun from_microsoft() {
        val Microsoft = "Microsoft"

        // block Microsoft
        add_number_rule(build_rule(Microsoft, "", 1, true, Def.FLAG_FOR_CALL))

        //  should block
        val r = Checker.checkCall(ctx, Microsoft)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)
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
        var r = Checker.checkSms(ctx, particularNumber, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_CONTENT, r.result)

        //  should block for anyone else
        r = Checker.checkSms(ctx, A, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.result)
    }

    // test regex flags IgnoreCase
    @Test
    fun regex_flags_ignore_case() {
        val msg = " Discount "

        // should pass without this flag
        add_content_rule(build_rule(".*discount.*", "", 1, true, Def.FLAG_FOR_SMS))
        var r = Checker.checkSms(ctx, B, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.result)

        // should block with this flag set
        add_content_rule(build_rule(".*discount.*", "", 1, true, Def.FLAG_FOR_SMS,
            patternFlags = Def.FLAG_REGEX_IGNORE_CASE))

        r = Checker.checkSms(ctx, B, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.result)
    }
    // test regex flags DotMatchAll
    @Test
    fun regex_flags_dot_match_all() {
        val msg = " http://\nabc.com "
        val rule = ".*http.*com.*"

        // should pass without this flag
        add_content_rule(build_rule(rule, "", 1, true, Def.FLAG_FOR_SMS))
        var r = Checker.checkSms(ctx, B, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.result)

        // should block with this flag set
        add_content_rule(build_rule(rule, "", 1, true, Def.FLAG_FOR_SMS,
            patternFlags = Def.FLAG_REGEX_DOT_MATCH_ALL))

        r = Checker.checkSms(ctx, B, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.result)
    }

}