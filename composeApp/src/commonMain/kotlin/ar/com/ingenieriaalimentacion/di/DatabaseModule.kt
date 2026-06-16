package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.data.db.DatabaseDriverFactory
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import org.koin.dsl.module

val databaseModule = module {
    single { AppDatabase(get<DatabaseDriverFactory>().create()) }
    single { get<AppDatabase>().pendingSubjectQueries }
    single { AppScope() }
    single { SyncCoordinator(repository = get(), connectivity = get(), scope = get()) }
}
