/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.notifications.intelligence.rules.data

import com.android.systemui.notifications.intelligence.rules.data.repository.ContactsRepository
import com.android.systemui.notifications.intelligence.rules.data.repository.ContactsRepositoryImpl
import com.android.systemui.notifications.intelligence.rules.data.repository.InstalledAppsRepository
import com.android.systemui.notifications.intelligence.rules.data.repository.InstalledAppsRepositoryImpl
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRulesRepository
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRulesRepositoryImpl
import dagger.Binds
import dagger.Module

@Module
interface NotificationRulesDataModule {
    @Binds public fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository

    @Binds
    public fun bindInstalledAppsRepository(
        impl: InstalledAppsRepositoryImpl
    ): InstalledAppsRepository

    @Binds
    public fun bindRulesRepository(
        impl: NotificationRulesRepositoryImpl
    ): NotificationRulesRepository
}
