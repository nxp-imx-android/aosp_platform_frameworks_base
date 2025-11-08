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

import static android.content.ComponentName.unflattenFromString;

import static com.android.server.pm.UserActivitiesAllowlist.DEBUG;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;

import com.android.server.ExpectableTestCase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

public final class UserActivitiesAllowlistTest
        extends GenericAllowlistTestCase<UserActivitiesAllowlist, ComponentName> {

    private static final String PERM_NAME_1 = "perm/one.is.the.loniest.number";
    private static final String PERM_NAME_2 = "perm/two.to.tango";
    private static final String PERM_NAME_3 = "perm/three.is.a.charm";

    private static final String TEMP_NAME_1 = "temp/one.is.the.loniest.number";
    private static final String TEMP_NAME_2 = "temp/two.to.tango";
    private static final String TEMP_NAME_3 = "temp/three.is.a.charm";

    private static final ComponentName PERM_ACTIVITY_1 = unflattenFromString(PERM_NAME_1);
    private static final ComponentName PERM_ACTIVITY_2 = unflattenFromString(PERM_NAME_2);
    private static final ComponentName PERM_ACTIVITY_3 = unflattenFromString(PERM_NAME_3);

    private static final ComponentName TEMP_ACTIVITY_1 = unflattenFromString(TEMP_NAME_1);
    private static final ComponentName TEMP_ACTIVITY_2 = unflattenFromString(TEMP_NAME_2);
    private static final ComponentName TEMP_ACTIVITY_3 = unflattenFromString(TEMP_NAME_3);

    private static final String SHORT_NAME = "i.am/.groot";
    private static final String FULL_NAME = "i.am/i.am.groot";
    private static final ComponentName ACTIVITY_SHORT_NAME = unflattenFromString(SHORT_NAME);
    private static final ComponentName ACTIVITY_FULL_NAME = unflattenFromString(FULL_NAME);


    private static final ComponentName NOT_ALLOWLISTED_ACTIVITY =
            unflattenFromString("allowlisted/I.am...NOT");
    private static final String INVALID_NAME = "invalid.I.am"; // missing package

    public UserActivitiesAllowlistTest() {
        super("activity", "activities");
    }

    @Override
    protected UserActivitiesAllowlist createAllowlist(String... configAllowlist) {
        return new UserActivitiesAllowlist(configAllowlist);
    }

    @Override
    protected String getInvalidName() {
        return INVALID_NAME;
    }

    @Override
    protected String getPermanentName1() {
        return PERM_NAME_1;
    }

    @Override
    protected String getPermanentName2() {
        return PERM_NAME_2;
    }

    @Override
    protected String getPermanentName3() {
        return PERM_NAME_3;
    }

    @Override
    protected String getTemporaryName1() {
        return TEMP_NAME_1;
    }

    @Override
    protected String getTemporaryName2() {
        return TEMP_NAME_2;
    }

    @Override
    protected String getTemporaryName3() {
        return TEMP_NAME_3;
    }

    @Override
    protected ComponentName getPermanentElement1() {
        return PERM_ACTIVITY_1;
    }

    @Override
    protected ComponentName getPermanentElement2() {
        return PERM_ACTIVITY_2;
    }

    @Override
    protected ComponentName getPermanentElement3() {
        return PERM_ACTIVITY_3;
    }

    @Override
    protected ComponentName getTemporaryElement1() {
        return TEMP_ACTIVITY_1;
    }

    @Override
    protected ComponentName getTemporaryElement2() {
        return TEMP_ACTIVITY_2;
    }

    @Override
    protected ComponentName getTemporaryElement3() {
        return TEMP_ACTIVITY_3;
    }

    @Override
    protected ComponentName getNotAllowlistedElement() {
        return NOT_ALLOWLISTED_ACTIVITY;
    }

    @Override
    protected String getShortName() {
        return SHORT_NAME;
    }

    @Override
    protected String getFullName() {
        return FULL_NAME;
    }

    @Override
    protected ComponentName getElementWithShortName() {
        return ACTIVITY_SHORT_NAME;
    }

    @Override
    protected ComponentName getElementWithFullName() {
        return ACTIVITY_FULL_NAME;
    }
}

// TODO(b/455912167): move to its own class on (separate CL)
/**
 * Not a test per se, but the base class for classes testing subclasses of {@link GenericAllowlist}.
 *
 * @param <A> type of the class being test.
 * @param <E> type of the allowlist element.
 */
abstract class GenericAllowlistTestCase<A extends GenericAllowlist<E>, E>
        extends ExpectableTestCase {

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    private final Context mRealContext =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();

    /** Need to spy the real context otherwise we'd have to mock resources, which is a PITA... */
    private final Context mSpiedContext = spy(mRealContext);

    @Mock
    private Resources mMockResources;

    private final String mSingular;
    private final String mPlural;

    protected GenericAllowlistTestCase(String singularName, String pluralName) {
        mSingular = singularName;
        mPlural = pluralName;
    }

    protected abstract String getPermanentName1();
    protected abstract String getPermanentName2();
    protected abstract String getPermanentName3();
    protected abstract E getPermanentElement1();
    protected abstract E getPermanentElement2();
    protected abstract E getPermanentElement3();

    protected abstract String getTemporaryName1();
    protected abstract String getTemporaryName2();
    protected abstract String getTemporaryName3();
    protected abstract E getTemporaryElement1();
    protected abstract E getTemporaryElement2();
    protected abstract E getTemporaryElement3();

    protected abstract String getInvalidName();

    protected abstract E getNotAllowlistedElement();

    // TODO(b/455912167): methods below are used to check activities that could be represented
    // by ether a full or "flattened" name. They might not apply to other subclass (like
    // notification), in which case we'd do add a new method (like assumeSupportShortName()) to
    // handle them.

    protected abstract String getShortName();
    protected abstract String getFullName();
    protected abstract E getElementWithShortName();
    protected abstract E getElementWithFullName();

    @Before
    public void setFixtures() {
        doReturn(mMockResources).when(mSpiedContext).getResources();
    }

    @Before
    public void assertFullAndShortElementsAreTheSame() {
        expectWithMessage("getElementWithFullName()")
                .that(getElementWithFullName()).isEqualTo(getElementWithShortName());
        expectWithMessage("getElementWithShortName()")
                .that(getElementWithShortName()).isEqualTo(getElementWithFullName());
    }

    @Test
    public final void testIsAllowed_null() throws Exception {
        var allowlist = createAllowlist();

        assertThrows(NullPointerException.class, () -> allowlist.isAllowed(null));
    }

    @Test
    public final void testIsAllowed_emptyConfig() throws Exception {
        var allowlist = createAllowlist();

        expectAllowed(allowlist, getPermanentElement1());
        expectEffectiveAllowlist(allowlist);
    }

    @Test
    public final void testIsAllowed_configWithOne() throws Exception {
        var allowlist = createAllowlist(getPermanentName1());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist, getPermanentElement1());
        expectEffectiveAllowlist(allowlist, getPermanentElement1());
    }

    @Test
    public final void testIsAllowed_configWithTwo() throws Exception {
        var allowlist = createAllowlist(getPermanentName1(), getPermanentName2());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist, getPermanentElement1(), getPermanentElement2());
        expectEffectiveAllowlist(allowlist, getPermanentElement1(), getPermanentElement2());
    }

    @Test
    public final void testIsAllowed_configWithThree() throws Exception {
        var allowlist =
                createAllowlist(getPermanentName1(), getPermanentName2(), getPermanentName3());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist,
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
        expectEffectiveAllowlist(allowlist,
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
    }

    @Test
    public final void testIsAllowed_configWithShortFullAndInvalidNames() throws Exception {
        var allowlist = createAllowlist(getShortName(), getFullName(), getInvalidName());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist, getElementWithFullName());
        expectEffectiveAllowlist(allowlist, getElementWithFullName());
    }

    @Test
    public final void testSetTemporaryAllowlist_fromEmptyConfig() throws Exception {
        var allowlist = createAllowlist();

        testSetAndResetTemporaryList(allowlist, () -> {
            expectAllowed(allowlist, getNotAllowlistedElement());
            expectEffectiveAllowlist(allowlist);
        });
    }

    @Test
    public final void testSetTemporaryAllowlist_fromConfigWithOne() throws Exception {
        var allowlist = createAllowlist(getPermanentName1());

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, getNotAllowlistedElement());
            expectAllowed(allowlist, getPermanentElement1());
            expectEffectiveAllowlist(allowlist, getPermanentElement1());
        });
    }

    @Test
    public final void testSetTemporaryAllowlist_fromConfigWithTwo() throws Exception {
        var allowlist = createAllowlist(getPermanentName1(), getPermanentName2());

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, getNotAllowlistedElement());
            expectAllowed(allowlist, getPermanentElement1(), getPermanentElement2());
            expectEffectiveAllowlist(allowlist, getPermanentElement1(), getPermanentElement2());
        });
    }

    @Test
    public final void testSetTemporaryAllowlist_fromConfigWithMultiple() throws Exception {
        var allowlist =
                createAllowlist(getPermanentName1(), getPermanentName2(), getPermanentName3());

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, getNotAllowlistedElement());
            expectAllowed(allowlist,
                    getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
            expectEffectiveAllowlist(allowlist,
                    getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
        });
    }

    private void testSetAndResetTemporaryList(A allowlist,
            Runnable permanentAllowListStateChecker) {
        // Check permanent state before
        permanentAllowListStateChecker.run();


        // Set as empty - everything should be allowed
        List<E> emptyList = emptyList();

        allowlist.setTemporaryAllowlist(emptyList);

        expectAllowedAfterSettingTemporaryList(allowlist, emptyList,
                getNotAllowlistedElement(),
                getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3(),
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3()
        );
        expectEffectiveAllowlist(allowlist);

        // One temporary element
        List<E> listWithOne = asList(getTemporaryElement1());

        allowlist.setTemporaryAllowlist(listWithOne);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithOne,
                getTemporaryElement1());
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithOne,
                getNotAllowlistedElement(), getTemporaryElement2(), getTemporaryElement3());
        expectDefaultPermanentElementsNotAllowed(allowlist);
        expectEffectiveAllowlist(allowlist, getTemporaryElement1());


        // Two temporary elements
        List<E> listWithOneAndTwo = asList(getTemporaryElement1(), getTemporaryElement2());

        allowlist.setTemporaryAllowlist(listWithOneAndTwo);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithOneAndTwo,
                getTemporaryElement1(), getTemporaryElement2());
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithOneAndTwo,
                getNotAllowlistedElement(), getTemporaryElement3());
        expectDefaultPermanentElementsNotAllowed(allowlist);
        expectEffectiveAllowlist(allowlist, getTemporaryElement1(), getTemporaryElement2());


        // Three temporary elements
        List<E> listWithThree =
                asList(getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3());

        allowlist.setTemporaryAllowlist(listWithThree);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithThree,
                getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3());
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithThree,
                getNotAllowlistedElement());
        expectDefaultPermanentElementsNotAllowed(allowlist);
        expectEffectiveAllowlist(allowlist,
                getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3());

        // Reset the list
        allowlist.setTemporaryAllowlist(null);

        permanentAllowListStateChecker.run();
    }

    @Test
    public final void testDump_emptyConfig() throws Exception {
        var allowlist = createAllowlist();

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: allowlisting disabled
                        permanent %s allowlist is empty.
                        temporary %s allowlist is not set.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural));
    }

    @Test
    public final void testDump_configWithOneElement() throws Exception {
        var name1 = getPermanentName1();
        var allowlist = createAllowlist(name1);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mSingular, name1, mPlural));
    }

    @Test
    public final void testDump_configWithMultipleElements() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var allowlist = createAllowlist(name1, name2);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural));
    }

    @Test
    public final void testDump_configWithOneInvalidElement() throws Exception {
        var allowlist = createAllowlist(getInvalidName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                        Dumpo:
                          id: %s
                          DEBUG: %b
                          %s allowlist status: allowlisting disabled
                          permanent %s allowlist is empty.
                          temporary %s allowlist is not set.
                             """,
                             allowlist, DEBUG, mPlural, mPlural, mPlural));
    }

    @Test
    public final void testDump_configWithValidAndInvalidElements() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var allowlist = createAllowlist(name1, name2, getInvalidName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural));
    }

    @Test
    public final void testDump_configWithFullName() throws Exception {
        // It's created with full name, but should dump the "normalized" short name
        var allowlist = createAllowlist(getFullName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mSingular, getShortName(), mPlural));
    }


    @Test
    public final void testDump_configWithShortAndFullNames() throws Exception {
        // Both are equivalent, so dump should only display the "normalized" one
        var allowlist = createAllowlist(getShortName(), getFullName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mSingular, getShortName(), mPlural));
    }

    @Test
    public final void testDump_temporaryAllowlistEmpty_emptyConfig() throws Exception {
        var allowlist = createAllowlist();
        allowlist.setTemporaryAllowlist(emptyList());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: allowlisting disabled
                        permanent %s allowlist is empty.
                        temporary %s allowlist is empty.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural));
    }

    @Test
    public final void testDump_temporaryAllowlistEmpty_nonEmptyConfig() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var allowlist = createAllowlist(name1, name2);
        allowlist.setTemporaryAllowlist(emptyList());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: allowlisting disabled
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist is empty.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural));
    }

    @Test
    public final void testDump_temporaryAllowlistWithOne_emptyConfig() throws Exception {
        var allowlist = createAllowlist();
        var name1 = getPermanentName1();
        allowlist.setTemporaryAllowlist(asList(allowlist.fromNormalizedName(name1)));

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: using temporary allowlist
                        permanent %s allowlist is empty.
                        temporary %s allowlist has 1 %s:
                          %s
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural, mSingular, name1));
    }

    @Test
    public final void testDump_temporaryAllowlistWithMultiple_nonEmptyConfig() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var tmpName1 = getPermanentName1();
        var tmpName2 = getTemporaryName2();
        var allowlist = createAllowlist(name1, name2);
        allowlist.setTemporaryAllowlist(asList(
                allowlist.fromNormalizedName(tmpName1),
                allowlist.fromNormalizedName(tmpName2)));

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        DEBUG: %b
                        %s allowlist status: using temporary allowlist
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist has 2 %s:
                          %s
                          %s
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural,
                           mPlural, tmpName1, tmpName2));
    }

    @Test
    public void testToString() {
        var allowlist1 = createAllowlist();
        var allowlist2 = createAllowlist();
        String toString1 = allowlist1.toString();
        String toString2 = allowlist2.toString();

        expectWithMessage("allowlist1.toString()").that(toString1).isNotEqualTo(toString2);
        expectWithMessage("allowlist1.toString()").that(toString1).contains("" + allowlist1.mId);
        expectWithMessage("allowlist2.toString()").that(toString2).contains("" + allowlist2.mId);
    }

    @Test
    public final void testNameToElementConversions() {
        testNormalizeAndBack(getPermanentName1(), getPermanentElement1());
        testNormalizeAndBack(getPermanentName2(), getPermanentElement2());
        testNormalizeAndBack(getPermanentName3(), getPermanentElement3());

        testNormalizeAndBack(getTemporaryName1(), getTemporaryElement1());
        testNormalizeAndBack(getTemporaryName2(), getTemporaryElement2());
        testNormalizeAndBack(getTemporaryName3(), getTemporaryElement3());

        testNormalizeAndBack(getShortName(), getElementWithShortName());
        // Cannot use testNormalizeAndBack on full name as they're normalized to short
        testConversion(getElementWithFullName(), getShortName());
        testConversion(getFullName(), getElementWithShortName());
    }

    // Asserts that originalElement is converted into expectedConvertedName (and returns the latter)
    private String testConversion(E originalElement, String expectedConvertedName) {
        A allowlist = createAllowlist();

        String convertedName = allowlist.toNormalizedName(originalElement);
        expectWithMessage("toNormalizedName(%s)", originalElement)
                .that(convertedName)
                .isEqualTo(expectedConvertedName);

        return convertedName;
    }

    // Asserts that originalName is converted into expectedConvertedElement (and returns the latter)
    private E testConversion(String originalName, E expectedConvertedElement) {
        A allowlist = createAllowlist();

        E convertedElement = allowlist.fromNormalizedName(originalName);
        expectWithMessage("fromNormalizedName(%s)", originalName)
                .that(convertedElement)
                .isEqualTo(expectedConvertedElement);

        return convertedElement;
    }

    // Asserts that originalName is converted into originalElement and vice versa
    private void testNormalizeAndBack(String originalName, E originalElement) {
        A allowlist = createAllowlist();

        String convertedName = testConversion(originalElement, originalName);
        E convertedElement = testConversion(originalName, originalElement);

        // convert back
        testConversion(convertedElement, originalName);
        testConversion(convertedName, originalElement);
    }

    protected abstract A createAllowlist(String... configAllowlist);

    @SafeVarargs
    private void expectNotAllowed(A allowlist, E... elements) {
        for (var element : elements) {
            expectWithMessage("isAllowed(%s)", element)
                    .that(allowlist.isAllowed(element))
                    .isFalse();
        }
    }

    @SafeVarargs
    private void expectAllowed(A allowlist, E... elements) {
        for (var element : elements) {
            expectWithMessage("isAllowed(%s)", element)
                    .that(allowlist.isAllowed(element))
                    .isTrue();
        }
    }

    @SafeVarargs
    private void expectNotAllowedAfterSettingTemporaryList(A allowlist,
            Collection<E> temporaryAllowList, E...elements) {
        for (var element: elements) {
            expectWithMessage("isAllowed(%s) after SetTemporaryAllowlist(%s)",
                    element, temporaryAllowList).that(allowlist.isAllowed(element)).isFalse();
        }
    }

    @SafeVarargs
    private void expectEffectiveAllowlist(A allowlist, E... elements) {
        expectWithMessage("getEffectiveAllowlist()")
                .that(allowlist.getEffectiveAllowlist())
                .containsExactlyElementsIn(elements);
    }

    @SafeVarargs
    private void expectAllowedAfterSettingTemporaryList(A allowlist,
            Collection<E> temporaryAllowList, E... elements) {
        for (var element: elements) {
            expectWithMessage("isAllowed(%s) after SetTemporaryAllowlist(%s)",
                    element, temporaryAllowList).that(allowlist.isAllowed(element)).isTrue();
        }
    }

    private void expectDefaultPermanentElementsNotAllowed(A allowlist) {
        expectNotAllowed(allowlist,
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
    }

    private String dump(A allowlist) throws IOException {
        try (StringWriter sw = new StringWriter()) {
            allowlist.dump(new PrintWriter(sw), /* prefix= */ "", /* header= */ "Dumpo");
            return sw.toString();
        }
    }
}
