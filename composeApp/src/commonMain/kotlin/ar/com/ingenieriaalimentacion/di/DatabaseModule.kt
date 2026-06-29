package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.data.db.DatabaseDriverFactory
import ar.com.ingenieriaalimentacion.data.db.SqlDelightDatabaseCleaner
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import org.koin.dsl.module

val databaseModule = module {
    single { AppDatabase(get<DatabaseDriverFactory>().create()) }
    single { get<AppDatabase>().pendingSubjectQueries }
    single { get<AppDatabase>().pendingMeasurementQueries }
    single { get<AppDatabase>().cachedEstablishmentQueries }
    single<DatabaseCleaner> { SqlDelightDatabaseCleaner(database = get()) }
    single { AppScope() }
    single<List<SyncEngine>> {
        listOf(get<SubjectRepository>(), get<MeasurementRepository>())
    }
    single { SyncCoordinator(engines = get(), connectivity = get(), scope = get()) }
}
