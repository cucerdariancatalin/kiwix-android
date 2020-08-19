/*
 * Kiwix Android
 * Copyright (c) 2019 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.kiwix.kiwixmobile.core.main

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import com.google.android.material.navigation.NavigationView
import org.kiwix.kiwixmobile.core.BuildConfig
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.base.BaseActivity
import org.kiwix.kiwixmobile.core.base.FragmentActivityExtensions
import org.kiwix.kiwixmobile.core.di.components.CoreActivityComponent
import org.kiwix.kiwixmobile.core.error.ErrorActivity
import org.kiwix.kiwixmobile.core.extensions.browserIntent
import org.kiwix.kiwixmobile.core.utils.ExternalLinkOpener
import javax.inject.Inject
import kotlin.system.exitProcess

const val KIWIX_SUPPORT_URL = "https://www.kiwix.org/support"
const val PAGE_URL_KEY = "pageUrl"
const val ZIM_FILE_URI_KEY = "zimFileUri"
const val KIWIX_INTERNAL_ERROR = 10

abstract class CoreMainActivity : BaseActivity(), WebViewProvider {

  @Inject lateinit var externalLinkOpener: ExternalLinkOpener
  protected var drawerToggle: ActionBarDrawerToggle? = null
  abstract val navController: NavController
  abstract val drawerContainerLayout: DrawerLayout
  abstract val drawerNavView: NavigationView
  abstract val bookmarksFragmentResId: Int
  abstract val settingsFragmentResId: Int
  abstract val historyFragmentResId: Int
  abstract val helpFragmentResId: Int
  abstract val cachedComponent: CoreActivityComponent
  abstract val topLevelDestinations: Set<Int>

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.KiwixTheme)
    super.onCreate(savedInstanceState)
    if (!BuildConfig.DEBUG) {
      val appContext = applicationContext
      Thread.setDefaultUncaughtExceptionHandler { paramThread: Thread?,
        paramThrowable: Throwable? ->
        val intent = Intent(appContext, ErrorActivity::class.java)
        val extras = Bundle()
        extras.putSerializable(ErrorActivity.EXCEPTION_KEY, paramThrowable)
        intent.putExtras(extras)
        appContext.startActivity(intent)
        finish()
        Process.killProcess(Process.myPid())
        exitProcess(KIWIX_INTERNAL_ERROR)
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    activeFragments().forEach { it.onActivityResult(requestCode, resultCode, data) }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    activeFragments().forEach {
      it.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (drawerToggle?.isDrawerIndicatorEnabled == true) {
      return drawerToggle?.onOptionsItemSelected(item) == true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onActionModeStarted(mode: ActionMode) {
    super.onActionModeStarted(mode)
    activeFragments().filterIsInstance<FragmentActivityExtensions>().forEach {
      it.onActionModeStarted(mode, this)
    }
  }

  override fun onActionModeFinished(mode: ActionMode) {
    super.onActionModeFinished(mode)
    activeFragments().filterIsInstance<FragmentActivityExtensions>().forEach {
      it.onActionModeFinished(mode, this)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    activeFragments().filterIsInstance<FragmentActivityExtensions>().forEach {
      it.onNewIntent(intent, this)
    }
  }

  override fun getCurrentWebView(): KiwixWebView? {
    return activeFragments().filterIsInstance<WebViewProvider>().firstOrNull()
      ?.getCurrentWebView()
  }

  override fun onSupportNavigateUp(): Boolean =
    navController.navigateUp() || super.onSupportNavigateUp()

  open fun setupDrawerToggle(toolbar: Toolbar) {
    drawerToggle =
      ActionBarDrawerToggle(
        this,
        drawerContainerLayout,
        R.string.open_drawer,
        R.string.close_drawer
      )
    drawerToggle?.let {
      drawerContainerLayout.addDrawerListener(it)
      it.syncState()
    }
    drawerContainerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
  }

  open fun disableDrawer() {
    drawerToggle?.isDrawerIndicatorEnabled = false
    drawerContainerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
  }

  open fun onNavigationItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_support_kiwix -> openSupportKiwixExternalLink()
      R.id.menu_settings -> openSettings()
      R.id.menu_help -> openHelpFragment()
      R.id.menu_history -> openHistoryActivity()
      R.id.menu_bookmarks_list -> openBookmarksActivity()
      else -> return false
    }
    return true
  }

  private fun openHelpFragment() {
    navigate(helpFragmentResId)
    handleDrawerOnNavigation()
  }

  private fun navigationDrawerIsOpen(): Boolean =
    drawerContainerLayout.isDrawerOpen(drawerNavView)

  fun closeNavigationDrawer() {
    drawerContainerLayout.closeDrawer(drawerNavView)
  }

  private fun openSupportKiwixExternalLink() {
    externalLinkOpener.openExternalUrl(KIWIX_SUPPORT_URL.toUri().browserIntent())
  }

  override fun onBackPressed() {
    if (navigationDrawerIsOpen()) {
      closeNavigationDrawer()
      return
    }
    if (activeFragments().filterIsInstance<FragmentActivityExtensions>().isEmpty()) {
      return super.onBackPressed()
    }
    activeFragments().filterIsInstance<FragmentActivityExtensions>().forEach {
      if (it.onBackPressed(this) == FragmentActivityExtensions.Super.ShouldCall) {
        super.onBackPressed()
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {

    if (activeFragments().filterIsInstance<FragmentActivityExtensions>().isEmpty()) {
      return super.onCreateOptionsMenu(menu)
    }
    var returnValue = true
    activeFragments().filterIsInstance<FragmentActivityExtensions>().forEach {
      if (it.onCreateOptionsMenu(menu, this) == FragmentActivityExtensions.Super.ShouldCall) {
        returnValue = super.onCreateOptionsMenu(menu)
      }
    }
    return returnValue
  }

  private fun activeFragments(): MutableList<Fragment> =
    supportFragmentManager.fragments

  fun navigate(action: NavDirections) {
    navController.navigate(action)
  }

  fun navigate(fragmentId: Int) {
    navController.navigate(fragmentId)
  }

  fun navigate(fragmentId: Int, bundle: Bundle) {
    navController.navigate(fragmentId, bundle)
  }

  private fun openSettings() {
    handleDrawerOnNavigation()
    navigate(settingsFragmentResId)
  }

  private fun openHistoryActivity() {
    navigate(historyFragmentResId)
  }

  private fun openBookmarksActivity() {
    navigate(bookmarksFragmentResId)
    handleDrawerOnNavigation()
  }

  protected fun handleDrawerOnNavigation() {
    closeNavigationDrawer()
    disableDrawer()
  }

  abstract fun openPage(pageUrl: String, zimFilePath: String = "")
}
