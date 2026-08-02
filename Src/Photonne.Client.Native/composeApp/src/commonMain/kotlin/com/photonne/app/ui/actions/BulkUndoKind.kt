package com.photonne.app.ui.actions

/**
 * Acciones en bloque que se pueden revertir por completo, y por tanto se
 * ejecutan sin preguntar y ofrecen "Deshacer" en vez de un diálogo previo.
 *
 * Cada valor nombra lo que se HIZO; deshacerlo es aplicar su inversa.
 */
enum class BulkUndoKind { Trash, Archive, Unarchive }
