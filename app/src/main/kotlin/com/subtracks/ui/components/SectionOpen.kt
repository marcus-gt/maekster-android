package com.subtracks.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal const val SECTION_OPEN_ARROW_TAG = "sectionOpenArrow"

/** The arrow that marks a header which opens its full section. It mirrors in right-to-left layouts. */
@Composable
fun SectionOpenArrow(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(24.dp).testTag(SECTION_OPEN_ARROW_TAG),
    )
}

/**
 * Makes a section header open its section: a button with a section-specific click label, at least
 * the 48 dp minimum touch height.
 */
fun Modifier.sectionOpenAction(
    label: String,
    onClick: () -> Unit,
): Modifier = heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
