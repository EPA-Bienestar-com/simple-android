package org.simple.clinic.facility

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.user.LoggedInUserFacilityMapping
import org.simple.clinic.user.User
import org.simple.clinic.util.Rules
import java.util.UUID
import javax.inject.Inject


class FacilityRepositoryAndroidTest {

  @Inject
  lateinit var database: AppDatabase

  @Inject
  lateinit var repository: FacilityRepository

  @Inject
  lateinit var testData: TestData

  @Inject
  lateinit var facilityDao: Facility.RoomDao

  @Inject
  lateinit var userDao: User.RoomDao

  @get:Rule
  val rule: RuleChain = Rules.global()

  private val user: User by lazy { testData.loggedInUser(uuid = UUID.fromString("3e78fb25-2ee2-442a-b7aa-21a87e067b9d")) }

  @Before
  fun setup() {
    TestClinicApp.appComponent().inject(this)
    database.clearAllTables()
    database.userDao().createOrUpdate(user)
  }

  @After
  fun tearDown() {
    database.clearAllTables()
  }

  @Test
  fun facilities_should_be_ordered_alphabetically() {
    val facilityB = testData.facility(uuid = UUID.randomUUID(), name = "Facility B")
    val facilityD = testData.facility(uuid = UUID.randomUUID(), name = "Phacility D")
    val facilityA = testData.facility(uuid = UUID.randomUUID(), name = "Facility A")
    val facilityC = testData.facility(uuid = UUID.randomUUID(), name = "Phacility C")

    val facilitiesToStore = listOf(facilityB, facilityD, facilityA, facilityC)
    facilityDao.save(facilitiesToStore)

    val allStoredFacilities = repository.facilities().blockingFirst()
    assertThat(allStoredFacilities).isEqualTo(listOf(facilityA, facilityB, facilityC, facilityD))

    val allFilteredFacilities = repository.facilities(searchQuery = "Pha").blockingFirst()
    assertThat(allFilteredFacilities).isEqualTo(listOf(facilityC, facilityD))
  }

  @Test
  fun when_associating_a_user_with_facilities_then_only_one_facility_should_be_set_as_current_facility() {
    val facility1 = testData.facility(uuid = UUID.randomUUID(), name = "facility1")
    val facility2 = testData.facility(uuid = UUID.randomUUID(), name = "facility2")
    val facility3 = testData.facility(uuid = UUID.randomUUID(), name = "facility3")
    val facility4 = testData.facility(uuid = UUID.randomUUID(), name = "facility4")

    val facilities = listOf(facility1, facility2, facility3, facility4)
    facilityDao.save(facilities)

    val facilityIds = facilities.map { it.uuid }
    repository.associateUserWithFacilities(user, facilityIds).blockingAwait()
    repository.setCurrentFacility(user, facility3).blockingAwait()
    repository.setCurrentFacility(user, facility4).blockingAwait()

    val currentFacility = repository.currentFacility(user).blockingFirst()
    assertThat(currentFacility).isEqualTo(facility4)
  }

  @Test
  fun when_changing_the_current_facility_for_a_user_then_the_current_facility_should_get_set() {
    val facility1 = testData.facility(uuid = UUID.randomUUID())
    val facility2 = testData.facility(uuid = UUID.randomUUID())
    val facility3 = testData.facility(uuid = UUID.randomUUID())
    val facilities = listOf(facility1, facility2, facility3)
    facilityDao.save(facilities)

    repository.associateUserWithFacilities(user, facilities.map { it.uuid })
        .andThen(repository.setCurrentFacility(user, facility2))
        .andThen(repository.setCurrentFacility(user, facility3))
        .blockingAwait()

    val mappings = userDao.mappingsForUser(user.uuid).blockingFirst()

    val facility3Mapping = mappings.first { it.facilityUuid == facility3.uuid }
    assertThat(facility3Mapping.isCurrentFacility).isTrue()
  }

  @Test
  fun when_changing_the_facility_for_a_user_the_previous_current_facility_should_get_unset() {
    val facility1 = testData.facility(uuid = UUID.randomUUID())
    val facility2 = testData.facility(uuid = UUID.randomUUID())
    val facility3 = testData.facility(uuid = UUID.randomUUID())
    val facilities = listOf(facility1, facility2, facility3)
    facilityDao.save(facilities)

    repository.associateUserWithFacilities(user, facilities.map { it.uuid })
        .andThen(repository.setCurrentFacility(user, facility2))
        .andThen(repository.setCurrentFacility(user, facility3))
        .blockingAwait()

    val mappings = userDao.mappingsForUser(user.uuid).blockingFirst()

    val facility1Mapping = mappings.first { it.facilityUuid == facility1.uuid }

    assertThat(facility1Mapping.isCurrentFacility).isFalse()
    val facility2Mapping = mappings.first { it.facilityUuid == facility2.uuid }
    assertThat(facility2Mapping.isCurrentFacility).isFalse()
  }

  @Test(expected = AssertionError::class)
  fun when_changing_the_facility_for_a_user_to_a_facility_whose_mapping_does_not_exist_then_an_error_should_be_thrown() {
    val facility1 = testData.facility(uuid = UUID.randomUUID())
    val facility2 = testData.facility(uuid = UUID.randomUUID())
    val facilities = listOf(facility1, facility2)
    facilityDao.save(facilities)

    repository.associateUserWithFacility(user, facility1).blockingAwait()
    userDao.changeCurrentFacility(user.uuid, newCurrentFacilityUuid = facility2.uuid)
  }

  @Test
  fun facilities_should_be_overridable() {
    val facility = testData.facility(uuid = UUID.randomUUID(), name = "Faceeleety")
    facilityDao.save(listOf(facility))

    val correctedFacility = facility.copy(name = "Facility")
    facilityDao.save(listOf(correctedFacility))

    val storedFacility = facilityDao.getOne(correctedFacility.uuid)!!
    assertThat(storedFacility.name).isEqualTo(correctedFacility.name)
  }

  @Test
  fun when_search_query_is_blank_then_all_facilities_should_be_fetched() {
    val facility1 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 1")
    val facility2 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 2")
    val facilities = listOf(facility1, facility2)
    facilityDao.save(facilities)

    val filteredFacilities = repository.facilities(searchQuery = "").blockingFirst()
    assertThat(filteredFacilities).isEqualTo(facilities)
  }

  @Test
  fun when_filtering_by_current_group_and_search_query_is_blank_then_all_facilities_should_be_fetched() {
    val facilityGroup1 = UUID.randomUUID()
    val facilityGroup2 = UUID.randomUUID()

    val facility1 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 1", groupUuid = facilityGroup1)
    val facility2 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 2", groupUuid = facilityGroup1)
    val facility3 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 3", groupUuid = facilityGroup2)

    val facilitiesInGroup1 = listOf(facility1, facility2)
    val facilitiesInGroup2 = listOf(facility3)
    facilityDao.save(facilitiesInGroup1 + facilitiesInGroup2)

    associateCurrentFacilityToUser(user, facilitiesInGroup1.first())

    val filteredFacilities = repository.facilitiesInCurrentGroup(searchQuery = "", user = user).blockingFirst()
    assertThat(filteredFacilities).isEqualTo(facilitiesInGroup1)
  }

  private fun associateCurrentFacilityToUser(user: User, facility: Facility) {
    repository
        .associateUserWithFacility(user, facility)
        .andThen(repository.setCurrentFacility(user, facility))
        .blockingAwait()
  }

  @Test
  fun when_search_query_is_not_blank_then_filtered_facilities_should_be_fetched() {
    val facility1 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 1")
    val facility2 = testData.facility(uuid = UUID.randomUUID(), name = "Phacility 2")
    val facility3 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 3")
    val facility4 = testData.facility(uuid = UUID.randomUUID(), name = "Phacility 4")
    facilityDao.save(listOf(facility1, facility2, facility3, facility4))

    val filteredFacilities = repository.facilities(searchQuery = "hac").blockingFirst()
    assertThat(filteredFacilities).isEqualTo(listOf(facility2, facility4))
  }

  @Test
  fun when_filtering_by_current_group_and_search_query_is_not_blank_then_filtered_facilities_should_be_fetched() {
    val facilityGroup1 = UUID.randomUUID()
    val facilityGroup2 = UUID.randomUUID()

    val facility1 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 1", groupUuid = facilityGroup1)
    val facility2 = testData.facility(uuid = UUID.randomUUID(), name = "Phacility 2", groupUuid = facilityGroup2)
    val facility3 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 3", groupUuid = facilityGroup1)
    val facility4 = testData.facility(uuid = UUID.randomUUID(), name = "Phacility 4", groupUuid = facilityGroup1)

    val facilitiesInGroup1 = listOf(facility1, facility3, facility4)
    val facilitiesInGroup2 = listOf(facility2)
    facilityDao.save(facilitiesInGroup1 + facilitiesInGroup2)

    associateCurrentFacilityToUser(user, facilitiesInGroup2.first())

    val filteredFacilities = repository.facilitiesInCurrentGroup(searchQuery = "hac", user = user).blockingFirst()
    assertThat(filteredFacilities).isEqualTo(listOf(facility2))
  }

  @Test
  fun filtering_of_facilities_should_be_case_insensitive() {
    val facility1 = testData.facility(uuid = UUID.randomUUID(), name = "Facility 1")
    val facility2 = testData.facility(uuid = UUID.randomUUID(), name = "Phacility 2")
    val facilities = listOf(facility1, facility2)
    facilityDao.save(facilities)

    val filteredFacilities = repository.facilities(searchQuery = "fac").blockingFirst()
    assertThat(filteredFacilities).isEqualTo(listOf(facility1))
  }

  @Test
  fun filtering_of_facilities_in_current_group_should_be_case_insensitive() {
    val facilityGroup1 = UUID.randomUUID()
    val facilityGroup2 = UUID.randomUUID()

    val group1Facility = testData.facility(uuid = UUID.randomUUID(), name = "Facility 1", groupUuid = facilityGroup1)
    val group2Facility = testData.facility(uuid = UUID.randomUUID(), name = "Phacility 2", groupUuid = facilityGroup2)
    val facilities = listOf(group1Facility, group2Facility)
    facilityDao.save(facilities)

    associateCurrentFacilityToUser(user, group1Facility)

    val filteredFacilities = repository.facilitiesInCurrentGroup(searchQuery = "fac", user = user).blockingFirst()
    assertThat(filteredFacilities).isEqualTo(listOf(group1Facility))
  }

  @Test
  fun getting_current_facility_immediately_for_the_given_user() {
    val facility1 = testData.facility(
        uuid = UUID.fromString("19822126-a96d-4619-b7be-4477f1f5e429"),
        name = "Facility 1"
    )
    val facility2 = testData.facility(
        uuid = UUID.fromString("978bd4f6-3f13-4ef7-847a-ad315dcd46fa"),
        name = "Facility 2"
    )

    repository.save(listOf(facility1, facility2)).blockingAwait()

    associateCurrentFacilityToUser(user, facility1)

    val currentFacility = repository.currentFacilityImmediate(user)
    assertThat(currentFacility).isEqualTo(facility1)
  }
}
