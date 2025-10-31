package spam.blocker.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import spam.blocker.db.IApi
import spam.blocker.db.QueryApi
import spam.blocker.db.ReportApi
import spam.blocker.service.bot.BackupExport
import spam.blocker.service.bot.BackupImport
import spam.blocker.service.bot.CalendarEvent
import spam.blocker.service.bot.CallEvent
import spam.blocker.service.bot.CallThrottling
import spam.blocker.service.bot.CategoryConfig
import spam.blocker.service.bot.CleanupHistory
import spam.blocker.service.bot.CleanupSpamDB
import spam.blocker.service.bot.ConvertNumber
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.Delay
import spam.blocker.service.bot.EnableApp
import spam.blocker.service.bot.EnableWorkflow
import spam.blocker.service.bot.FilterSpamResult
import spam.blocker.service.bot.FindRules
import spam.blocker.service.bot.GenerateTag
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.ISchedule
import spam.blocker.service.bot.ImportAsRegexRule
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.InterceptSms
import spam.blocker.service.bot.ModifyNumber
import spam.blocker.service.bot.ModifyRules
import spam.blocker.service.bot.ParseCSV
import spam.blocker.service.bot.ParseQueryResult
import spam.blocker.service.bot.ParseXML
import spam.blocker.service.bot.Periodically
import spam.blocker.service.bot.QuickTile
import spam.blocker.service.bot.ReadFile
import spam.blocker.service.bot.RegexExtract
import spam.blocker.service.bot.ScheduledAutoReportNumber
import spam.blocker.service.bot.Ringtone
import spam.blocker.service.bot.SmsEvent
import spam.blocker.service.bot.SmsThrottling
import spam.blocker.service.bot.Weekly
import spam.blocker.service.bot.WriteFile

val myModule = SerializersModule {
    polymorphic(ISchedule::class) {
        subclass(Daily::class)
        subclass(Weekly::class)
        subclass(Periodically::class)
        subclass(Delay::class)
    }
    polymorphic(IAction::class) {
        subclass(CleanupHistory::class)
        subclass(HttpDownload::class)
        subclass(CleanupSpamDB::class)
        subclass(BackupExport::class)
        subclass(BackupImport::class)
        subclass(ReadFile::class)
        subclass(WriteFile::class)
        subclass(ParseCSV::class)
        subclass(ParseXML::class)
        subclass(RegexExtract::class)
        subclass(ImportToSpamDB::class)
        subclass(ImportAsRegexRule::class)
        subclass(ConvertNumber::class)
        subclass(FindRules::class)
        subclass(ModifyRules::class)
        subclass(EnableWorkflow::class)
        subclass(EnableApp::class)
        subclass(ParseQueryResult::class)
        subclass(FilterSpamResult::class)
        subclass(InterceptCall::class)
        subclass(InterceptSms::class)
        subclass(ScheduledAutoReportNumber::class)
        subclass(CategoryConfig::class)
        subclass(CalendarEvent::class)
        subclass(SmsEvent::class)
        subclass(CallEvent::class)
        subclass(ModifyNumber::class)
        subclass(CallThrottling::class)
        subclass(SmsThrottling::class)
        subclass(Ringtone::class)
        subclass(QuickTile::class)
        subclass(GenerateTag::class)
    }
    polymorphic(IApi::class) {
        subclass(QueryApi::class)
        subclass(ReportApi::class)
    }
}

// A json serializer that supports all interfaces(IAction, IApi, ...) in this app
val InterfaceJson =  Json {
    this.serializersModule = myModule
    encodeDefaults = true
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}

val InterfacePrettyJson =  Json {
    prettyPrint = true

    this.serializersModule = myModule
    encodeDefaults = true
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}