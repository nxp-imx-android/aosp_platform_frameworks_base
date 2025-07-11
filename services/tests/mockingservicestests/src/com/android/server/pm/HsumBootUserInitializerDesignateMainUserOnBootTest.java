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
    private final boolean mFlagDemoteMainUser;
    private final boolean mConfigCreateInitialUser;
    private final boolean mConfigDesignateMainUser;
    private final boolean mConfigIsMainUserPermanentAdmin;
    private final boolean mResult;

    // NOTE: not really "Generated code", but that's the only why to calm down checkstyle, otherwise
    // it will complain they should be all upper case
    /** Useless javadoc to make checkstyle happy... */
    @Parameters(name =
            "{index}: dbgBuild={0},sysprop={1},flag={2},cfgCreateIU={3},cfgDesignateMU={4},cfgIsMUPermAdm={5},result={6}")
    public static Collection<Object[]> junitParametersPassedToConstructor() {
        // TODO(b/402486365): refactor code below so we define one set (for non-debuggable), then
        // copy it over the other 3 combinations.
        return Arrays.asList(new Object[][] {
                // Note: entries below are broken in 3 lines to make them easier to read / maintain:
                // - build type and emulation
                // - input (configs)
                // - expected output

                // User build, sysprop not set
                // original (only 2 configs used)
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                // added FLAG and CFG_CREATE_INITIAL
                // FLAG(false), CFG_CREATE_INITIAL(true) - everything but first equals to original
                {
                        // This is special case used to guard the config by the flag (RESULT true)
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                // FLAG(true), CFG_CREATE_INITIAL(false) - everything equals to original
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                // FLAG(true), CFG_CREATE_INITIAL(true) - everything equals to original
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },

                // User build, sysprop set - everything should be the same as above
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        // This is special case used to guard the config by the flag (RESULT true)
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(false), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },

                // Debuggable build - result should be value of property (false)
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        // This is special case used to guard the config by the flag (RESULT true)
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(false)
                },
                {
                        DEBUGGABLE(true), SYSPROP(false),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(false)
                },

                // Debuggable build - result should be value of property (true)
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        // This is special case used to guard the config by the flag (RESULT true)
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(false), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(false), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(false), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(false),
                        RESULT(true)
                },
                {
                        DEBUGGABLE(true), SYSPROP(true),
                        FLAG(true), CFG_CREATE_INITIAL(true), CFG_DESIGNATE_MAIN(true), CFG_IS_PERM_ADM(true),
                        RESULT(true)
                },
        });
    }

    public HsumBootUserInitializerDesignateMainUserOnBootTest(boolean isDebuggable,
            boolean sysPropDesignateMainUser, boolean flagDemoteMainUser,
            boolean configCreateInitialUser, boolean configDesignateMainUser,
            boolean configIsMainUserPermanentAdmin,  boolean result) {
        mSysPropDesignateMainUser = sysPropDesignateMainUser;
        mIsDebuggable = isDebuggable;
        mFlagDemoteMainUser = flagDemoteMainUser;
        mConfigCreateInitialUser = configCreateInitialUser;
        mConfigDesignateMainUser = configDesignateMainUser;
        mConfigIsMainUserPermanentAdmin = configIsMainUserPermanentAdmin;
        mResult = result;
        Log.v(mTag, "Constructor: isDebuggable=" + isDebuggable
                + ", sysPropDesignateMainUser=" + sysPropDesignateMainUser
                + ", flagDemoteMainUser=" + flagDemoteMainUser
                + ", configCreateInitialUser=" + configCreateInitialUser
                + ", configDesignateMainUser=" + configDesignateMainUser
                + ", configIsMainUserPermanentAdmin=" + configIsMainUserPermanentAdmin
                + ", result=" + result);
    }

    @Test
    public void testDesignateMainUserOnBoot() {
        mockSysPropDesignateMainUser(mSysPropDesignateMainUser);
        mockIsDebuggable(mIsDebuggable);
        setDemoteMainUserFlag(mFlagDemoteMainUser);
        mockConfigDesignateMainUser(mConfigDesignateMainUser);
        mockConfigIsMainUserPermanentAdmin(mConfigIsMainUserPermanentAdmin);
        mockConfigCreateInitialUser(mConfigCreateInitialUser);

        boolean result = designateMainUserOnBoot(mMockContext);

        expect.withMessage("designateMainUserOnBoot()").that(result).isEqualTo(mResult);
    }
}
