package spam.blocker.service

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import spam.blocker.db.CallTable
import spam.blocker.db.ContentFilterTable
import spam.blocker.db.Db
import spam.blocker.db.Flag
import spam.blocker.db.NumberFilterTable
import spam.blocker.db.PatternFilter
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
        spf.setAllowRepeated(false)
        spf.setRepeatedConfig(1, 5)
        spf.setRecentAppList(listOf())
        spf.setRecentAppConfig(5)
        NumberFilterTable().clearAll(ctx)
        ContentFilterTable().clearAll(ctx)
    }
    @After
    fun teardown() {}

    fun build_rule(
        pattern: String, patternExtra: String, priority: Int, isBlacklist: Boolean, flagCallSms: Int,
        patternFlags: Int = 0, patternExtraFlags: Int = 0
    ) : PatternFilter {
        val f = PatternFilter()
        f.pattern = pattern
        f.patternExtra = patternExtra
        f.priority = priority
        f.isBlacklist = isBlacklist
        f.flagCallSms = Flag(flagCallSms)
        f.patternFlags = Flag(patternFlags)
        f.patternExtraFlags = Flag(patternExtraFlags)

        return f
    }

    fun add_number_rule(f: PatternFilter) {
        NumberFilterTable().addNewPatternFilter(ctx, f)
    }
    fun add_content_rule(f: PatternFilter) {
        ContentFilterTable().addNewPatternFilter(ctx, f)
    }

    fun nameOf(number: String) : String {
        return "Name_of_$number"
    }
    private fun mock_contact(rawNumber: String) {
        val ci = ContactInfo()
        ci.rawPhone = rawNumber
        ci.name = nameOf(rawNumber)

        mockkObject(Permission)
        every { Permission.isContactsPermissionGranted(ctx) } returns true

        mockkObject(Contacts)
        every { Contacts.findAllEndWith(ctx, any()) } answers {
            val num = secondArg<String>()
            if (Util.clearNumber(rawNumber).endsWith(num))
                Contacts.Companion.Wrapper(arrayListOf(ci))
            else
                Contacts.Companion.Wrapper( arrayListOf() )
        }
    }

    private fun mock_advance_time(durationMinutes: Int) {
        mockkObject(Time)
        every { Time.getCurrentTimeMilliss() } answers { System.currentTimeMillis() + durationMinutes*60*1000}
    }
    private fun add_call_record(rawNumber:String, result: Int, reason: String) {
        val call = Record()
        call.peer = rawNumber
        call.time = System.currentTimeMillis()
        call.result = result
        call.reason = reason
        CallTable().addNewRecord(ctx, call)
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
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, SpamChecker.checkCall(ctx, A).result)

        // non-contact: block
        assertEquals(Def.RESULT_BLOCKED_BY_NUMBER, SpamChecker.checkCall(ctx, B).result)
    }

    // Non Contact -> block
    @Test
    fun contact_exclusive() {
        spf.setContactEnabled(true)
        spf.setContactExclusive(true)
        mock_contact(A)

        // contact: pass
        assertEquals(Def.RESULT_ALLOWED_BY_CONTACT, SpamChecker.checkCall(ctx, A).result)

        // non-contact: block
        assertEquals(Def.RESULT_BLOCKED_BY_NON_CONTACT, SpamChecker.checkCall(ctx, B).result)
    }

    // testing contact contains special characters
    @Test
    fun contact_with_special_characters() {
        val C = "+1 222-333 4444" // Contact with Special Characters
        val incomingCalls = listOf("2223334444", "12223334444", "+12223334444", "+1 222 333-4444", "1 222 333 4 4 4 4")

        spf.setContactEnabled(true)
        spf.setContactExclusive(false)
        mock_contact(C)

        // all match
        incomingCalls.forEach {
            assertEquals("should match: <$C> - <$it>", Def.RESULT_ALLOWED_BY_CONTACT, SpamChecker.checkCall(ctx, it).result)
        }
        // should block
        assertEquals("should block <$C> - <$B>", Def.RESULT_ALLOWED_BY_DEFAULT, SpamChecker.checkCall(ctx, B).result)
    }

    // testing repeated call
    @Test
    fun repeated_call() {
        spf.setAllowRepeated(true)
        spf.setRepeatedConfig(3, 5)

        // block all number
        add_number_rule(build_rule(".*", "", 1, true, Def.FLAG_FOR_CALL))

        // should fail 3 times
        for (i in 1..3) {
            val r = SpamChecker.checkCall(ctx, B)
            assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)
            add_call_record(B, r.result, r.reason())
        }

        // the 4th call should pass
        val r = SpamChecker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_REPEATED, r.result)
        add_call_record(B, r.result, r.reason())

        // should block again after 6 minutes
        mock_advance_time(6)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, SpamChecker.checkCall(ctx, B).result)
    }


    private fun mock_recent_app(pkgs: List<String>, expire: Long) {
        mockkObject(Permission)
        every { Permission.listUsedAppWithinXSecond(ctx, any()) } answers {
            if (Time.getCurrentTimeMilliss() < expire)
                pkgs
            else
                listOf()
        }
    }
    // testing contact contains special characters
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
        var r = SpamChecker.checkCall(ctx, B)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_RECENT_APP, r.result)

        // 5 min expired
        mock_advance_time(6)

        // should block
        r = SpamChecker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)

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
        var r = SpamChecker.checkCall(ctx, A)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_NUMBER, r.result)

        // B should block
        r = SpamChecker.checkCall(ctx, B)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_NUMBER, r.result)
    }

    // test incoming number like "Microsoft"
    @Test
    fun from_microsoft() {
        val Microsoft = "Microsoft"

        // block Microsoft
        add_number_rule(build_rule(Microsoft, "", 1, true, Def.FLAG_FOR_CALL))

        //  should block
        val r = SpamChecker.checkCall(ctx, Microsoft)
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
        var r = SpamChecker.checkSms(ctx, particularNumber, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_CONTENT, r.result)

        //  should block for anyone else
        r = SpamChecker.checkSms(ctx, A, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.result)
    }

    // test regex flags IgnoreCase
    @Test
    fun regex_flags_ignore_case() {
        val msg = " Discount "

        // should pass without this flag
        add_content_rule(build_rule(".*discount.*", "", 1, true, Def.FLAG_FOR_SMS))
        var r = SpamChecker.checkSms(ctx, B, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.result)

        // should block with this flag set
        add_content_rule(build_rule(".*discount.*", "", 1, true, Def.FLAG_FOR_SMS,
            patternFlags = Def.FLAG_REGEX_IGNORE_CASE))

        r = SpamChecker.checkSms(ctx, B, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.result)
    }
    // test regex flags DotMatchAll
    @Test
    fun regex_flags_dot_match_all() {
        val msg = " http://\nabc.com "
        val rule = ".*http.*com.*"

        // should pass without this flag
        add_content_rule(build_rule(rule, "", 1, true, Def.FLAG_FOR_SMS))
        var r = SpamChecker.checkSms(ctx, B, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.result)

        // should block with this flag set
        add_content_rule(build_rule(rule, "", 1, true, Def.FLAG_FOR_SMS,
            patternFlags = Def.FLAG_REGEX_DOT_MATCH_ALL))

        r = SpamChecker.checkSms(ctx, B, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.result)
    }

    // test regex flags Literal
    @Test
    fun regex_flags_literal() {
        val msg = "SomeSuper*"
        val rule = "SomeSuper*"

        // should pass without this flag
        add_content_rule(build_rule(rule, "", 1, true, Def.FLAG_FOR_SMS))
        var r = SpamChecker.checkSms(ctx, B, msg)
        assertEquals("should pass", Def.RESULT_ALLOWED_BY_DEFAULT, r.result)

        // should block with this flag set
        add_content_rule(build_rule(rule, "", 1, true, Def.FLAG_FOR_SMS,
            patternFlags = Def.FLAG_REGEX_LITERAL))

        r = SpamChecker.checkSms(ctx, B, msg)
        assertEquals("should block", Def.RESULT_BLOCKED_BY_CONTENT, r.result)
    }
}