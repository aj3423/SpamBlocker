package spam.blocker.service.bot

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass


val botModule = SerializersModule {
    polymorphic(ISchedule::class) {
        subclass(Daily::class)
        subclass(Weekly::class)
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
        subclass(ImportToSpamDB::class)
        subclass(ImportAsRegexRule::class)
    }
}

// A json serializer that supports ISchedule and IAction
val botJson =  Json {
    serializersModule = botModule
    encodeDefaults = true
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}

