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

package com.android.printspooler.outofprocess.tests;

import static android.print.test.Utils.eventually;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.pdf.PrintedPdfDocument;
import android.print.test.BasePrintTest;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Tests that verify properties of the "Save as PDF" printer. */
@RunWith(AndroidJUnit4.class)
public class PdfPrinterTest extends BasePrintTest {
    private static final String LOG_TAG = PdfPrinterTest.class.getSimpleName();
    private static final String LIST_VIEW = "android.widget.ListView";
    private static final int MAX_SWIPE = 100;
    private static final String PDF_PRINTER = "Save as PDF";
    private static final String EXPANDED_CONTROL_ID = "com.android.printspooler:id/duplex_spinner";

    interface InterruptableConsumer<T> {
        void accept(T t) throws InterruptedException;
    }

    public static UiDevice getUiDevice() {
        return UiDevice.getInstance(getInstrumentation());
    }

    /**
     * Execute {@code waiter} until {@code condition} is met.
     *
     * @param condition Conditions to wait for
     * @param waiter Code to execute while waiting
     */
    private void waitWithTimeout(Supplier<Boolean> condition, InterruptableConsumer<Long> waiter)
            throws TimeoutException, InterruptedException {
        long startTime = System.currentTimeMillis();
        while (condition.get()) {
            long timeLeft = OPERATION_TIMEOUT_MILLIS - (System.currentTimeMillis() - startTime);
            if (timeLeft < 0) {
                throw new TimeoutException();
            }

            waiter.accept(timeLeft);
        }
    }

    /**
     * Start print operation that just prints a single empty page
     *
     * @param printAttributesRef Where to store the reference to the print attributes once started
     */
    private PrintJob print(PrintAttributes[] printAttributesRef) {
        return print(
                new PrintDocumentAdapter() {
                    @Override
                    public void onStart() {}

                    @Override
                    public void onLayout(
                            PrintAttributes oldAttributes,
                            PrintAttributes newAttributes,
                            CancellationSignal cancellationSignal,
                            LayoutResultCallback callback,
                            Bundle extras) {
                        callback.onLayoutFinished(
                                (new PrintDocumentInfo.Builder(PRINT_JOB_NAME)).build(),
                                !newAttributes.equals(printAttributesRef[0]));

                        synchronized (printAttributesRef) {
                            printAttributesRef[0] = newAttributes;
                            printAttributesRef.notifyAll();
                        }
                    }

                    @Override
                    public void onWrite(
                            PageRange[] pages,
                            ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal,
                            WriteResultCallback callback) {
                        try {
                            try {
                                PrintedPdfDocument document =
                                        new PrintedPdfDocument(
                                                getActivity(), printAttributesRef[0]);
                                try {
                                    PdfDocument.Page page = document.startPage(0);
                                    document.finishPage(page);
                                    try (FileOutputStream os =
                                            new FileOutputStream(destination.getFileDescriptor())) {
                                        document.writeTo(os);
                                        os.flush();
                                    }
                                } finally {
                                    document.close();
                                }
                            } finally {
                                destination.close();
                            }

                            callback.onWriteFinished(pages);
                        } catch (IOException e) {
                            callback.onWriteFailed(e.getMessage());
                        }
                    }
                },
                (PrintAttributes) null);
    }

    /**
     * Select 'option' from drop down list (i.e. spinner).
     *
     * @param spinnerObj
     * @param option
     * @throws UiObjectNotFoundException
     */
    private void selectSpinnerOption(UiObject2 spinnerObj, String option)
            throws UiObjectNotFoundException {
        spinnerObj.click();
        UiObject2 listView =
                getUiDevice().wait(Until.findObject(By.clazz(LIST_VIEW)), OPERATION_TIMEOUT_MILLIS);
        UiScrollable scrollableListView = new UiScrollable(new UiSelector().className(LIST_VIEW));
        UiObject2 optionObj = getUiDevice().findObject(By.text(option));
        if (optionObj == null) {
            int tries = 2;
            while (tries > 0) {
                Boolean ifFind = scrollableListView.scrollTextIntoView(option);
                if (!ifFind && tries == 2) {
                    scrollableListView.scrollToBeginning(MAX_SWIPE);
                    tries--;
                } else if (!ifFind) {
                    // If failed to find 'option' in the spinner drop down list.
                    // Click any item to get back to normal view and throw exception.
                    listView = getUiDevice().findObject(By.clazz(LIST_VIEW).enabled(true));
                    if (listView != null && listView.getChildCount() > 0) {
                        listView.getChildren().get(0).click();
                    }
                    throw new UiObjectNotFoundException("Fail to find option: " + option);
                } else {
                    break;
                }
            }
            optionObj = getUiDevice().findObject(By.text(option));
        }
        if (optionObj != null) {
            optionObj.click();
        }
    }

    private void selectPdfPrinter() throws UiObjectNotFoundException {
        UiObject2 destinationSpinner =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/destination_spinner")),
                                OPERATION_TIMEOUT_MILLIS);
        getUiDevice().waitForIdle();
        selectSpinnerOption(destinationSpinner, PDF_PRINTER);
        getUiDevice()
                .wait(
                        Until.hasObject(By.res("com.android.printspooler:id/print_button")),
                        OPERATION_TIMEOUT_MILLIS);
    }

    private void closePrintOptions() {
        // Make sure a known-expanded control is visible.
        getUiDevice().waitForIdle();
        UiObject2 spinner =
                getUiDevice().findObject(By.res(EXPANDED_CONTROL_ID));
        if (spinner == null) {
            Log.i(LOG_TAG, "Print options are already closed");
            return;
        }

        // Click the expander handle.
        UiObject2 handle =
                getUiDevice()
                        .findObject(By.res("com.android.printspooler:id/expand_collapse_handle"));
        if (handle != null) {
            handle.click();
        }

        // Wait for the known-expanded control to be gone before reporting complete.
        getUiDevice()
                .wait(
                        Until.gone(By.res(EXPANDED_CONTROL_ID)),
                        OPERATION_TIMEOUT_MILLIS);
        getUiDevice().waitForIdle();
    }

    @Test
    @LargeTest
    public void duplexNotSupported() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        mPrintHelper.openPrintOptions();
        getUiDevice().waitForIdle();

        // If the duplex mode spinner has a child named None and it isn't enabled,
        // then duplexing must not be available.
        UiObject2 spinner =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/duplex_spinner")),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(spinner);
        assertEquals(1, spinner.getChildCount());
        assertNotNull(spinner.findObject(By.text("None")));
        assertFalse(spinner.isEnabled());

        closePrintOptions();

        // Close the print dialog.
        getUiDevice().pressBack();
        eventually(() -> assertTrue(job.isCancelled()));
    }

    @Test
    @LargeTest
    public void commonSizesAvailable() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];
        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        mPrintHelper.openPrintOptions();
        getUiDevice().waitForIdle();

        UiObject2 spinner =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/paper_size_spinner")),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(spinner);
        for (String size : new String[] {"ISO A4", "Letter"}) {
            try {
                Log.i(LOG_TAG, "Checking for paper size " + size);
                synchronized (printAttributes) {
                    PrintAttributes old = printAttributes[0];
                    selectSpinnerOption(spinner, size);
                    getUiDevice().waitForIdle();
                    Log.d(LOG_TAG, "Waiting for print attributes to change");
                    waitWithTimeout(
                            () -> printAttributes[0] == null || printAttributes[0] == old,
                            printAttributes::wait);
                }
            } catch (UiObjectNotFoundException e) {
                assertFalse("Size " + size + " is missing", true);
            }
        }

        closePrintOptions();

        // Close the print dialog.
        getUiDevice().pressBack();
        eventually(() -> assertTrue(job.isCancelled()));
    }
}
