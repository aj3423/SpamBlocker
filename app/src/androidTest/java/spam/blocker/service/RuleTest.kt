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
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.service.checker.Checker
import spam.blocker.util.ContactInfo
import spam.blocker.util.Contacts
import spam.blocker.util.Now
import spam.blocker.util.Permission
import spam.blocker.util.PhoneNumber
import spam.blocker.util.TimeUtils
import spam.blocker.util.Util
import spam.blocker.util.Util.CallInfo
import spam.blocker.util.spf
import spam.blocker.util.spf.RecentAppInfo
import java.util.Calendar


class RuleTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    val Alice = "111" // Allow
    val Bob = "222" // Block

    // This is executed before every test case runs
    @Before
    fun setup() {
        clearAllMocks()

        spf.SharedPref(ctx).clear()
        NumberRegexTable().clearAll(ctx)
        ContentRegexTable().clearAll(ctx)
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
        schedule: String = "",
        simSlot: Int? = null,
    ): RegexRule {
        val r = RegexRule()

        r.pattern = pattern
        r.patternExtra = patternExtra
        r.priority = priority
        r.isBlacklist = isBlacklist
        r.flags = flagCallSms
        r.patternFlags = patternFlags
        r.patternExtraFlags = patternExtraFlags
        r.schedule = schedule
        r.simSlot = simSlot

        return r
    }

    fun add_number_rule(r: RegexRule) {
        NumberRegexTable().addNewRule(ctx, r)
    }

    fun add_content_rule(r: RegexRule) {
        ContentRegexTable().addNewRule(ctx, r)
    }


    private fun mock_contact(rawNumber: String) {
        val ci = ContactInfo(
            id = "",
            name = "Mock_contact_$rawNumber"
        )

        mockkObject(Permission)
        every { Permission.contacts.isGranted } returns true

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
        every { Permission.callLog.isGranted } returns true
    }

    private fun mock_phone_state_permission_granted() {
        every { Permission.phoneState.isGranted } returns true
    }

    private fun mock_sms_permission_granted() {
        every { Permission.readSMS.isGranted } returns true
    }

    private fun mock_calls(
        rawNumber: String,
        direction: Int,
        repeatedTimes: Int,
        atTimeMillis: Long
    ) {
        val number = PhoneNumber(ctx, rawNumber)
        every { Util.getHistoryCallsByNumber(any(), number, direction, any()) } answers {
            val withinMillis = lastArg<Long>()
            val mockNow = Now.currentMillis()
            if (atTimeMillis in mockNow - withinMillis..mockNow) {
                List<CallInfo>(repeatedTimes) { CallInfo(rawNumber = rawNumber, type = 0, duration = 20)}
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
        every { Util.countHistorySMSByNumber(any(), number, direction, any()) } answers {
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

    // Contact > blacklist
    @Test
    fun contact_lenient() {
        val spf = spf.Contact(ctx)
        spf.isEnabled = true
        spf.isStrict = false
        mock_contact(Alice)
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // contact: pass
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, Checker.checkCall(ctx, Alice).type)

        // non-contact: block
        assertEquals(Def.RESULT_BLOCKED_BY_NUMBER_REGEX, Checker.checkCall(ctx, Bob).type)
    }

    // Non Contact -> block
    @Test
    fun contact_strict() {
        val spf = spf.Contact(ctx)
        spf.isEnabled = true
        spf.isStrict = true
        mock_contact(Alice)

        // contact: pass
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, Checker.checkCall(ctx, Alice).type)

        // non-contact: block
        assertEquals(
            Def.RESULT_BLOCKED_BY_NON_CONTACT,
            Checker.checkCall(ctx, Bob).type
        )
    }

    // testing repeated call
    @Test
    fun repeated_call() {
        spf.RepeatedCall(ctx).apply {
            isEnabled = true
            times = 4
            inXMin = 5
        }

        mockkObject(Permission)
        mockkObject(Util)
        mock_call_permission_granted()
        mock_sms_permission_granted()

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // mock no call/sms record
        val now = System.currentTimeMillis()
        val twoMinAgo = now - 2 * 60 * 1000
        mock_calls(Bob, Def.DIRECTION_INCOMING, 0, twoMinAgo)
        mock_sms(Bob, Def.DIRECTION_INCOMING, 0, twoMinAgo)

        // should always fail when no history records
        for (i in 1..3) {
            val r = Checker.checkCall(ctx, Bob)
            assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)
        }

        // add two calls
        mock_calls(Bob, Def.DIRECTION_INCOMING, 2, twoMinAgo)

        // should still fail, 2<4
        val r2 = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r2.type)

        // add two sms
        mock_sms(Bob, Def.DIRECTION_INCOMING, 2, twoMinAgo)
        // should pass, 2+2>=4
        val r3 = Checker.checkCall(ctx, Bob)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_REPEATED, r3.type)

        // should block again after 10 minutes
        mock_advance_time_by_minutes(10)
        val r4 = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r4.type)
    }

    // testing dialed
    @Test
    fun dialed() {
        val spf = spf.Dialed(ctx)
        val inXdays = 5
        spf.isEnabled = true
        spf.days = inXdays

        mockkObject(Permission)
        mockkObject(Util)
        mock_call_permission_granted()
        mock_sms_permission_granted()

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // mock no call/sms record
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2 * 24 * 3600 * 1000
        mock_calls(Bob, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)
        mock_sms(Bob, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)

        // should always fail when no history records
        for (i in 1..3) {
            val r = Checker.checkCall(ctx, Bob)
            assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)
        }

        // add a call
        mock_calls(Bob, Def.DIRECTION_OUTGOING, 1, twoDaysAgo)

        // should pass
        val r1 = Checker.checkCall(ctx, Bob)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DIALED, r1.type)


        // clear that call, add an sms
        mock_calls(Bob, Def.DIRECTION_OUTGOING, 0, twoDaysAgo)
        mock_sms(Bob, Def.DIRECTION_OUTGOING, 1, twoDaysAgo)

        // should pass
        val r2 = Checker.checkCall(ctx, Bob)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DIALED, r2.type)

        // should block again after 10 days
        mock_advance_time_by_minutes(10 * 24 * 60)
        val r4 = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r4.type)
    }

    private fun mock_recent_app(pkgs: List<String>, expire: Long) {
        mockkObject(Permission)
        every { Util.listUsedAppWithinXSecond(ctx, any()) } answers {
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
        spf.inXMin = 5
        spf.setList(pkgs.map { RecentAppInfo(it) })

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // app being used
        mock_recent_app(pkgs, System.currentTimeMillis() + 5 * 60 * 1000)

        // should pass
        var r = Checker.checkCall(ctx, Bob)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_RECENT_APP, r.type)

        // 5 min expired
        mock_advance_time_by_minutes(6)

        // should block
        r = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)

    }

    fun mock_current_hour_min(hour: Int, min: Int) {
        mockkObject(TimeUtils)

        every { TimeUtils.currentHourMin() } returns Pair(hour, min)
    }

    @Test
    fun off_time() {
        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // should block
        var r = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)

        // set off-time to (1:00, 2:00)
        val spf = spf.OffTime(ctx).apply {
            isEnabled = true

            startHour = 1
            startMin = 0
            endHour = 2
            endMin = 0
        }

        // mock current time to 1:15
        mock_current_hour_min(1, 15)

        // should pass
        r = Checker.checkCall(ctx, Bob)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_OFF_TIME, r.type)

        // mock current time to 3:00
        mock_current_hour_min(3, 0)

        // should block
        r = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)


        // ---- test start > end (over night) ----
        spf.startHour = 21
        spf.startMin = 0 // 21 PM -> 02 AM
        spf.endHour = 2
        spf.endMin = 0
        // mock current time to 22:00
        mock_current_hour_min(22, 0)
        // should pass
        r = Checker.checkCall(ctx, Bob)
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
        var r = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)

        // should pass, Thursday
        mock_current_time_millis(getTimestampInMilliseconds(3, 1, 20))
        r = Checker.checkCall(ctx, Bob)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

    }

    // testing call number rules
    @Test
    fun number_rules_priority() {
        // block all number, priority 1
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))
        // allow A, priority: 2
        add_number_rule(build_rule(Alice, "", 2, false, Def.FLAG_FOR_CALL))

        // set B as Contact
        mock_contact(Bob)
        // block B, priority: 100
        add_number_rule(build_rule("$Bob.*", "", 100, true, Def.FLAG_FOR_CALL))

        // A should pass
        var r = Checker.checkCall(ctx, Alice)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_NUMBER_REGEX, r.type)

        // B should block
        r = Checker.checkCall(ctx, Bob)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)
    }

    // test incoming number like "Microsoft"
    @Test
    fun from_microsoft() {
        val Microsoft = "Microsoft"

        // block Microsoft
        add_number_rule(build_rule(Microsoft, "", 1, true, Def.FLAG_FOR_CALL))

        //  should block
        val r = Checker.checkCall(ctx, Microsoft)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)
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
        var r = Checker.checkSms(ctx, rawNumber = particularNumber, messageBody = msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_CONTENT_RULE, r.type)

        //  should block for anyone else
        r = Checker.checkSms(ctx, Alice, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT_RULE, r.type)
    }

    // test regex flags CaseSensitive
    @Test
    fun regex_flags_case_sensitive() {
        val msg = " Discount "

        // should pass with this flag set
        add_content_rule(build_rule(".*discount.*", "", 1, true, Def.FLAG_FOR_SMS,
            patternFlags = Def.FLAG_REGEX_CASE_SENSITIVE))
        var r = Checker.checkSms(ctx, Bob, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        // should block without this flag
        add_content_rule(
            build_rule(
                ".*discount.*", "", 1, true, Def.FLAG_FOR_SMS,
                patternFlags = 0
            )
        )

        r = Checker.checkSms(ctx, Bob, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT_RULE, r.type)
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
        var r = Checker.checkCall(ctx, Alice)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        //  should block for leading 0
        r = Checker.checkCall(ctx, domesticNumber)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)
    }

    // regex flags IgnoreCC
    @Test
    fun regex_flags_ignore_cc() {
        val internationalNumber = "+3312345"

        add_number_rule(
            build_rule("123.*", "", 2, true, Def.FLAG_FOR_CALL,
                patternFlags = Def.FLAG_REGEX_IGNORE_CC)
        )

        // should pass by default
        var r = Checker.checkCall(ctx, Alice)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        // should pass if it doesn't have the leading +
        r = Checker.checkCall(ctx, internationalNumber.substring(1))
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        //  should block for leading 0
        r = Checker.checkCall(ctx, internationalNumber)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)
    }

    // Multi SIM number rule
    @Test
    fun multi_sim_number_rule() {
        mock_phone_state_permission_granted()

        // rule 1, allow number 123 for sim 0, priority 99
        add_number_rule(
            build_rule("123", "", 99, isBlacklist = false, Def.FLAG_FOR_CALL, simSlot = 0)
        )
        // rule 2, block all others, priority 0
        add_number_rule(
            build_rule(".*", "", 0, isBlacklist = true, Def.FLAG_FOR_CALL)
        )

        val number_123 = "123"
        // call from 123(sim 0) should pass (by rule 1)
        var r = Checker.checkCall(ctx, number_123, simSlot = 0)
        assertEquals("should pass 1", Def.RESULT_ALLOWED_BY_NUMBER_REGEX, r.type)

        // call from 123(sim 1) should be blocked (by rule 2)
        r = Checker.checkCall(ctx, number_123, simSlot = 1)
        assertEquals("should block 2", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)

        // call from 123(null simSlot) should be allowed by rule 1, simSlot==null for people only use 1 sim card
        r = Checker.checkCall(ctx, number_123)
        assertEquals("should block 3", Def.RESULT_ALLOWED_BY_NUMBER_REGEX, r.type)

        // call from random number(null simSlot) should be blocked by rule 2, simSlot==null for people only use 1 sim card
        val random_number = "random_number"
        r = Checker.checkCall(ctx, random_number)
        assertEquals("should block 4", Def.RESULT_BLOCKED_BY_NUMBER_REGEX, r.type)
    }

    // Multi SIM content rule
    // this function name can't be "multi_sim_content_rule", the test would fail because of the name...
    @Test
    fun wtf_multi_sim_content_rule() {
        mock_phone_state_permission_granted()

        // rule 1, allow sms 123 for sim 0, priority 99
        add_content_rule(
            build_rule("123", "", 99, isBlacklist = false, Def.FLAG_FOR_SMS, simSlot = 0)
        )
        // rule 2, block all others, priority 0
        add_content_rule(
            build_rule(".*", "", 0, isBlacklist = true, Def.FLAG_FOR_SMS)
        )

        val content_123 = "123"
        // sms content "123"(sim 0) should pass (by rule 1)
        var r = Checker.checkSms(ctx, Alice, content_123, simSlot = 0)
        assertEquals("should pass 1", Def.RESULT_ALLOWED_BY_CONTENT_RULE, r.type)

        // sms content "123"(sim 1) should be blocked (by rule 2)
        r = Checker.checkSms(ctx, Bob, content_123, simSlot = 1)
        assertEquals("should block 2", Def.RESULT_BLOCKED_BY_CONTENT_RULE, r.type)

        // sms "123"(null simSlot) should be allowed by rule 1, simSlot==null for people only use 1 sim card
        r = Checker.checkSms(ctx, Alice, content_123)
        assertEquals("should block 3", Def.RESULT_ALLOWED_BY_CONTENT_RULE, r.type)

        // sms with random content(null simSlot) should be blocked by rule 2, simSlot==null for people only use 1 sim card
        val random_sms = "random_sms"
        r = Checker.checkSms(ctx, Bob, random_sms)
        assertEquals("should block 4", Def.RESULT_BLOCKED_BY_CONTENT_RULE, r.type)
    }

    // CNAP rule (Caller Display Name)
    @Test
    fun cnap_rule() {
        // block by display name "block"
        add_number_rule(
            build_rule("block", "", 99, isBlacklist = true, Def.FLAG_FOR_CALL, patternFlags = Def.FLAG_REGEX_FOR_CNAP)
        )

        var r = Checker.checkCall(ctx, rawNumber = "", cnap = "block")
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CNAP_REGEX, r.type)

        // wrong CNAP
        r = Checker.checkCall(ctx, rawNumber = "", cnap = "nah")
        assertEquals("should pass by default", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)

        // empty CNAP
        r = Checker.checkCall(ctx, rawNumber = "", cnap = null)
        assertEquals("should pass by default", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)
    }

    // Geo Location rule
    @Test
    fun geo_location_rule() {
        mock_phone_state_permission_granted()

        // block by geo location "Texas"
        add_number_rule(
            build_rule("texas", "", 0, isBlacklist = true, Def.FLAG_FOR_CALL, patternFlags = Def.FLAG_REGEX_FOR_GEO_LOCATION)
        )

        // Texas
        var r = Checker.checkCall(ctx, rawNumber = "+18324004649")
        assertEquals("should block Texas", Def.RESULT_BLOCKED_BY_GEO_LOCATION_REGEX, r.type)

        // non-Texas
        r = Checker.checkCall(ctx, rawNumber = "")
        assertEquals("should allow non-Texas by default", Def.RESULT_ALLOWED_BY_DEFAULT, r.type)
    }
}