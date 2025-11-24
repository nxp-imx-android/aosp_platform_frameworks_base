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
        val longerMeasurable = measurables[0]
        val shorterMeasurable = measurables[1]

        // 1. Measure with infinite constraint to determine the natural desired width of the text.
        val unconstrained = Constraints(maxHeight = constraints.maxHeight)

        // 2. Decide which placeable to use.
        val selectedPlaceable =
            // Try the longer option first.
            longerMeasurable.measure(unconstrained).takeIf { it.fitsWithin(constraints) }

                // If the longer one didn't fit, try the shorter one.
                ?: shorterMeasurable.measure(unconstrained).takeIf { it.fitsWithin(constraints) }

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
