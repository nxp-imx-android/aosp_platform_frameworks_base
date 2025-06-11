/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.server.pm;

import static com.android.server.pm.HsumBootUserInitializer.designateMainUserOnBoot;

import android.util.Log;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

public final class HsumBootUserInitializerDesignateMainUserOnBootTest
        extends AbstractHsumBootUserInitializerConstructorHelpersTestCase {

    private final boolean mIsDebuggable;
    private final boolean mSysPropDesignateMainUser;
    private final boolean mConfigDesignateMainUser;
    private final boolean mConfigIsMainUserPermanentAdmin;
    private final boolean mResult;

    /** Useless javadoc to make checkstyle happy... */
    @Parameters(name =
            "{index}: dbgBuild={0},sysprop={1},cfgDesignateMU={2},cfgIsMUPermAdm={3},result={4}")
    public static Collection<Object[]> junitParametersPassedToConstructor() {
        return Arrays.asList(new Object[][] {
                // Note: entries below are broken in 3 lines to make them easier to read / maintain:
                // - build type and emulation
                // - input (configs)
                // - expected output

                // User build, sysprop not set
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },

                // User build, sysprop set - everything should be the same as above
                {
                        DEBUGGABLE(false), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },

                // Debuggable build - result should be value of property (false)
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },

                // Debuggable build - result should be value of property (true)
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
        });
    }

    public HsumBootUserInitializerDesignateMainUserOnBootTest(boolean isDebuggable,
            boolean sysPropDesignateMainUser, boolean configDesignateMainUser,
            boolean configIsMainUserPermanentAdmin, boolean result) {
        mConfigDesignateMainUser = configDesignateMainUser;
        mConfigIsMainUserPermanentAdmin = configIsMainUserPermanentAdmin;
        mSysPropDesignateMainUser = sysPropDesignateMainUser;
        mIsDebuggable = isDebuggable;
        mResult = result;
        Log.v(mTag, "Constructor: isDebuggable=" + isDebuggable
                + ", sysPropDesignateMainUser=" + sysPropDesignateMainUser
                + ", configDesignateMainUser=" + configDesignateMainUser
                + ", configIsMainUserPermanentAdmin=" + configIsMainUserPermanentAdmin
                + ", result=" + result);
    }

    @Test
    public void testDesignateMainUserOnBoot() {
        mockSysPropDesignateMainUser(mSysPropDesignateMainUser);
        mockIsDebuggable(mIsDebuggable);
        mockConfigDesignateMainUser(mConfigDesignateMainUser);
        mockConfigIsMainUserPermanentAdmin(mConfigIsMainUserPermanentAdmin);

        boolean result = designateMainUserOnBoot(mMockContext);

        expect.withMessage("designateMainUserOnBoot()").that(result).isEqualTo(mResult);
    }
}
