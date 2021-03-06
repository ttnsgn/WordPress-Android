package org.wordpress.android.ui.stats.refresh.lists.sections.granular.usecases

import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.R
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.time.ClicksModel
import org.wordpress.android.fluxc.model.stats.time.ClicksModel.Click
import org.wordpress.android.fluxc.model.stats.time.ClicksModel.Group
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.store.StatsStore.OnStatsFetched
import org.wordpress.android.fluxc.store.StatsStore.StatsError
import org.wordpress.android.fluxc.store.StatsStore.StatsErrorType.GENERIC_ERROR
import org.wordpress.android.fluxc.store.stats.time.ClicksStore
import org.wordpress.android.test
import org.wordpress.android.ui.stats.refresh.lists.BlockList
import org.wordpress.android.ui.stats.refresh.lists.Error
import org.wordpress.android.ui.stats.refresh.lists.StatsBlock
import org.wordpress.android.ui.stats.refresh.lists.StatsBlock.Type.BLOCK_LIST
import org.wordpress.android.ui.stats.refresh.lists.StatsBlock.Type.ERROR
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Divider
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.ExpandableItem
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Header
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Link
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.ListItemWithIcon
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Title
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.EXPANDABLE_ITEM
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.HEADER
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.LINK
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.LIST_ITEM_WITH_ICON
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.TITLE
import org.wordpress.android.ui.stats.refresh.lists.sections.granular.SelectedDateProvider
import org.wordpress.android.ui.stats.refresh.utils.StatsDateFormatter
import org.wordpress.android.ui.stats.refresh.utils.toFormattedString
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import java.util.Date

private const val pageSize = 6
private val statsGranularity = DAYS
private val selectedDate = Date(0)

class ClicksUseCaseTest : BaseUnitTest() {
    @Mock lateinit var store: ClicksStore
    @Mock lateinit var site: SiteModel
    @Mock lateinit var statsDateFormatter: StatsDateFormatter
    @Mock lateinit var selectedDateProvider: SelectedDateProvider
    @Mock lateinit var tracker: AnalyticsTrackerWrapper
    private lateinit var useCase: ClicksUseCase
    private val firstGroupViews = 50
    private val secondGroupViews = 30
    private val singleClick = Group("group1", "Group 1", "group1.jpg", "group1.com", firstGroupViews, listOf())
    private val click = Click("Click 1", 20, "click.jpg", "click.com")
    private val group = Group("group2", "Group 2", "group2.jpg", "group2.com", secondGroupViews, listOf(click))
    @Before
    fun setUp() {
        useCase = ClicksUseCase(
                statsGranularity,
                Dispatchers.Unconfined,
                store,
                selectedDateProvider,
                statsDateFormatter,
                tracker
        )
        whenever((selectedDateProvider.getSelectedDate(statsGranularity))).thenReturn(selectedDate)
    }

    @Test
    fun `maps clicks to UI model`() = test {
        val forced = false
        val model = ClicksModel(10, 15, listOf(singleClick, group), false)
        whenever(store.fetchClicks(site, pageSize, statsGranularity, selectedDate, forced)).thenReturn(
                OnStatsFetched(
                        model
                )
        )

        val result = loadData(true, forced)

        assertThat(result.type).isEqualTo(BLOCK_LIST)
        val expandableItem = (result as BlockList).assertNonExpandedList()

        expandableItem.onExpandClicked(true)

        val updatedResult = loadData(true, forced)

        (updatedResult as BlockList).assertExpandedList()
    }

    private fun BlockList.assertNonExpandedList(): ExpandableItem {
        assertThat(this.items).hasSize(4)
        assertTitle(this.items[0])
        assertHeader(this.items[1])
        assertSingleItem(
                this.items[2],
                singleClick.name!!,
                singleClick.views,
                singleClick.icon
        )
        return assertExpandableItem(this.items[3], group.name!!, group.views!!, group.icon)
    }

    private fun BlockList.assertExpandedList(): ExpandableItem {
        assertThat(this.items).hasSize(6)
        assertTitle(this.items[0])
        assertHeader(this.items[1])
        assertSingleItem(
                this.items[2],
                singleClick.name!!,
                singleClick.views,
                singleClick.icon
        )
        val expandableItem = assertExpandableItem(this.items[3], group.name!!, group.views!!, group.icon)
        assertSingleItem(this.items[4], click.name, click.views, click.icon)
        assertThat(this.items[5]).isEqualTo(Divider)
        return expandableItem
    }

    @Test
    fun `adds view more button when hasMore`() = test {
        val forced = false
        val model = ClicksModel(10, 15, listOf(singleClick), true)
        whenever(
                store.fetchClicks(site, pageSize, statsGranularity, selectedDate, forced)
        ).thenReturn(
                OnStatsFetched(
                        model
                )
        )
        val result = loadData(true, forced)

        assertThat(result.type).isEqualTo(BLOCK_LIST)
        (result as BlockList).apply {
            assertThat(this.items).hasSize(4)
            assertTitle(this.items[0])
            assertHeader(this.items[1])
            assertSingleItem(
                    this.items[2],
                    singleClick.name!!,
                    singleClick.views,
                    singleClick.icon
            )
            assertLink(this.items[3])
        }
    }

    @Test
    fun `maps empty clicks to UI model`() = test {
        val forced = false
        whenever(
                store.fetchClicks(site, pageSize, statsGranularity, selectedDate, forced)
        ).thenReturn(
                OnStatsFetched(ClicksModel(0, 0, listOf(), false))
        )

        val result = loadData(true, forced)

        assertThat(result.type).isEqualTo(BLOCK_LIST)
        (result as BlockList).apply {
            assertThat(this.items).hasSize(2)
            assertTitle(this.items[0])
            assertThat(this.items[1]).isEqualTo(BlockListItem.Empty(R.string.stats_no_data_for_period))
        }
    }

    @Test
    fun `maps error item to UI model`() = test {
        val forced = false
        val message = "Generic error"
        whenever(
                store.fetchClicks(site, pageSize, statsGranularity, selectedDate, forced)
        ).thenReturn(
                OnStatsFetched(
                        StatsError(GENERIC_ERROR, message)
                )
        )

        val result = loadData(true, forced)

        assertThat(result.type).isEqualTo(ERROR)
        (result as Error).apply {
            assertThat(this.errorMessage).isEqualTo(message)
        }
    }

    private fun assertTitle(item: BlockListItem) {
        assertThat(item.type).isEqualTo(TITLE)
        assertThat((item as Title).textResource).isEqualTo(R.string.stats_clicks)
    }

    private fun assertHeader(item: BlockListItem) {
        assertThat(item.type).isEqualTo(HEADER)
        assertThat((item as Header).leftLabel).isEqualTo(R.string.stats_clicks_link_label)
        assertThat(item.rightLabel).isEqualTo(R.string.stats_clicks_label)
    }

    private fun assertSingleItem(
        item: BlockListItem,
        key: String,
        views: Int?,
        icon: String?
    ) {
        assertThat(item.type).isEqualTo(LIST_ITEM_WITH_ICON)
        assertThat((item as ListItemWithIcon).text).isEqualTo(key)
        if (views != null) {
            assertThat(item.value).isEqualTo(views.toString())
        } else {
            assertThat(item.value).isNull()
        }
        assertThat(item.iconUrl).isEqualTo(icon)
    }

    private fun assertExpandableItem(
        item: BlockListItem,
        label: String,
        views: Int,
        icon: String?
    ): ExpandableItem {
        assertThat(item.type).isEqualTo(EXPANDABLE_ITEM)
        assertThat((item as ExpandableItem).header.text).isEqualTo(label)
        assertThat(item.header.value).isEqualTo(views.toFormattedString())
        assertThat(item.header.iconUrl).isEqualTo(icon)
        return item
    }

    private fun assertLink(item: BlockListItem) {
        assertThat(item.type).isEqualTo(LINK)
        assertThat((item as Link).text).isEqualTo(R.string.stats_insights_view_more)
    }

    private suspend fun loadData(refresh: Boolean, forced: Boolean): StatsBlock {
        var result: StatsBlock? = null
        useCase.liveData.observeForever { result = it }
        useCase.fetch(site, refresh, forced)
        return checkNotNull(result)
    }
}
