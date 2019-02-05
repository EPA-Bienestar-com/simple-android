package org.simple.clinic.reports

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import org.simple.clinic.storage.files.FileStorage
import org.simple.clinic.storage.files.GetFileResult
import org.simple.clinic.storage.files.WriteFileResult
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.ofType
import org.simple.clinic.util.toOptional
import java.io.File
import javax.inject.Inject
import javax.inject.Named

class ReportsRepository @Inject constructor(
    private val fileStorage: FileStorage,
    @Named("reports_file_path") private val reportsFilePath: String
) {
  private val fileChangedSubject = PublishSubject.create<Optional<File>>()

  fun reportsFile(): Observable<Optional<File>> {
    val initialFile = Observable
        .fromCallable { fileStorage.getFile(reportsFilePath) }
        .map { result ->
          if (result is GetFileResult.Success && result.file.length() > 0L) {
            Just(result.file)

          } else {
            None
          }
        }

    return fileChangedSubject.mergeWith(initialFile)
  }

  fun updateReports(reportContent: String): Completable =
      Single.fromCallable { fileStorage.getFile(reportsFilePath) }
          .ofType<GetFileResult.Success>()
          .map { fileStorage.writeToFile(it.file, reportContent) }
          .ofType(WriteFileResult.Success::class.java)
          .doOnSuccess { fileChangedSubject.onNext(it.file.toOptional()) }
          .ignoreElement()
}
