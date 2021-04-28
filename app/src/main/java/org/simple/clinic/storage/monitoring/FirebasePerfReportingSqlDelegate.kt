package org.simple.clinic.storage.monitoring

import android.content.ContentValues
import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.google.firebase.perf.FirebasePerformance
import org.simple.clinic.remoteconfig.ConfigReader
import javax.inject.Inject

class FirebasePerfReportingSqlDelegate(
    private val firebasePerformance: FirebasePerformance,
    private val daoInformationExtractor: DaoInformationExtractor,
    private val sampler: Sampler
) : SQLiteDatabaseSqlDelegate {

  @Inject
  constructor(
      firebasePerformance: FirebasePerformance,
      daoInformationExtractor: DaoInformationExtractor,
      remoteConfig: ConfigReader
  ) : this(
      firebasePerformance = firebasePerformance,
      daoInformationExtractor = daoInformationExtractor,
      sampler = Sampler(remoteConfig.double("room_query_profile_sample_rate", 0.0).toFloat())
  )

  override fun query(
      database: SupportSQLiteDatabase,
      query: String?
  ): Cursor {
    return reportTimeTaken { database.query(query) }
  }

  override fun query(
      database: SupportSQLiteDatabase,
      query: String?,
      bindArgs: Array<out Any>?
  ): Cursor {
    return reportTimeTaken { database.query(query, bindArgs) }
  }

  override fun query(
      database: SupportSQLiteDatabase,
      query: SupportSQLiteQuery?
  ): Cursor {
    return reportTimeTaken { database.query(query) }
  }

  override fun query(
      database: SupportSQLiteDatabase,
      query: SupportSQLiteQuery?,
      cancellationSignal: CancellationSignal?
  ): Cursor {
    return reportTimeTaken { database.query(query, cancellationSignal) }
  }

  override fun insert(
      database: SupportSQLiteDatabase,
      table: String?,
      conflictAlgorithm: Int,
      values: ContentValues?
  ): Long {
    return reportTimeTaken { database.insert(table, conflictAlgorithm, values) }
  }

  override fun delete(
      database: SupportSQLiteDatabase,
      table: String?,
      whereClause: String?,
      whereArgs: Array<out Any>?
  ): Int {
    return reportTimeTaken { database.delete(table, whereClause, whereArgs) }
  }

  override fun update(
      database: SupportSQLiteDatabase,
      table: String?,
      conflictAlgorithm: Int,
      values: ContentValues?,
      whereClause: String?,
      whereArgs: Array<out Any>?
  ): Int {
    return reportTimeTaken { database.update(table, conflictAlgorithm, values, whereClause, whereArgs) }
  }

  override fun execSQL(
      database: SupportSQLiteDatabase,
      sql: String?
  ) {
    reportTimeTaken { database.execSQL(sql) }
  }

  override fun execSQL(
      database: SupportSQLiteDatabase,
      sql: String?,
      bindArgs: Array<out Any>?
  ) {
    reportTimeTaken { database.execSQL(sql, bindArgs) }
  }

  private inline fun <reified R> reportTimeTaken(
      operation: () -> R
  ): R {
    return if (sampler.sample)
      executeAndReport(operation)
    else
      operation()
  }

  private inline fun <reified R> executeAndReport(
      operation: () -> R
  ): R {
    val daoInformation = daoInformationExtractor.findDaoMethodInCurrentCallStack()

    return if (daoInformation != null)
      runSqlOperationWithReporting(daoInformation, operation)
    else
      operation()
  }

  private inline fun <reified R> runSqlOperationWithReporting(
      daoInformation: DaoInformationExtractor.DaoMethodInformation,
      operation: () -> R
  ): R {
    val trace = firebasePerformance
        .newTrace("SqlOperation")
        .apply {
          putAttribute("dao", daoInformation.daoName)
          putAttribute("method", daoInformation.methodName)
        }

    trace.start()
    val result = operation()
    trace.stop()

    return result
  }
}
