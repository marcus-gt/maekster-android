package com.subtracks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subtracks.ui.theme.MaeksterShapes
import com.subtracks.ui.theme.MaeksterSpacing
import com.subtracks.ui.theme.MaeksterTheme

/** A small capsule for a release facet under an album header (a genre or a record label) that opens its albums. */
@Composable
fun MetadataChip(
    text: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDescription: String? = null,
) {
    // A 32dp capsule inside a 48dp touch target, like Material's own chips.
    Row(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 32.dp)
                .clip(MaeksterShapes.pill)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                .padding(horizontal = MaeksterSpacing.m, vertical = MaeksterSpacing.s - 2.dp),
        horizontalArrangement = Arrangement.spacedBy(MaeksterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = iconDescription, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaeksterTheme.text.metadata,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
