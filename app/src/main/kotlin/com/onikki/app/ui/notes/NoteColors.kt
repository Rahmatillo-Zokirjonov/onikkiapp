package com.onikki.app.ui.notes

import androidx.compose.ui.graphics.Color
import com.onikki.app.data.db.entity.NoteColor

/** Card / editor background: a dark, low-saturation tint that keeps the "Tun" palette readable. */
fun NoteColor.tint(): Color = when (this) {
    NoteColor.QIZIL -> Color(0xFF35202A)
    NoteColor.TOQSARIQ -> Color(0xFF372619)
    NoteColor.SARIQ -> Color(0xFF34301C)
    NoteColor.YASHIL -> Color(0xFF1C3027)
    NoteColor.MOVIY -> Color(0xFF1B2A3F)
    NoteColor.BINAFSHA -> Color(0xFF2A2340)
    NoteColor.KULRANG -> Color(0xFF262B36)
}

/** Swatch / filter dot. */
fun NoteColor.dot(): Color = when (this) {
    NoteColor.QIZIL -> Color(0xFFE0707A)
    NoteColor.TOQSARIQ -> Color(0xFFEE9B5C)
    NoteColor.SARIQ -> Color(0xFFE3C45A)
    NoteColor.YASHIL -> Color(0xFF63BE88)
    NoteColor.MOVIY -> Color(0xFF6AA4E6)
    NoteColor.BINAFSHA -> Color(0xFFA68BE0)
    NoteColor.KULRANG -> Color(0xFF9AA1AE)
}
