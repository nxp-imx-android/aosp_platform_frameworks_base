package com.android.systemui.shade.ui.composable

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VariableDayDate(
    longerDateText: String,
    shorterDateText: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized,
) {
    Layout(
        content = {
            Text(
                text = longerDateText,
                style = textStyle,
                color = textColor,
                softWrap = false,
                maxLines = 1,
            )
            Text(
                text = shorterDateText,
                style = textStyle,
                color = textColor,
                softWrap = false,
                maxLines = 1,
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        check(measurables.size == 2)

        // 1. Measure with infinite constraint to determine the natural desired width of the text.
        val unconstrained = Constraints(maxHeight = constraints.maxHeight)

        // We MUST measure both measurables even if the first one fits.
        // Conditional measurement causes issues with LookaheadScope: if logic diverges between the
        // lookahead pass and the approach pass (e.g. during animations), both measurables need to
        // have been measured to avoid crashes or layout bugs (see b/463395847).
        val longer = measurables[0].measure(unconstrained)
        val shorter = measurables[1].measure(unconstrained)

        // 2. Decide which placeable to use.
        val selectedPlaceable =
            when {
                longer.fitsWithin(constraints) -> longer
                shorter.fitsWithin(constraints) -> shorter
                else -> null
            }

        if (selectedPlaceable == null) {
            // Neither fits, render nothing.
            layout(0, 0) {}
        } else {
            layout(selectedPlaceable.width, selectedPlaceable.height) {
                selectedPlaceable.placeRelative(0, 0)
            }
        }
    }
}

private fun Placeable.fitsWithin(constraints: Constraints): Boolean =
    width <= constraints.maxWidth && height <= constraints.maxHeight
