package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Franja del borde izquierdo que el sistema se reserva para su propio gesto.
 *
 * En Android con navegación por gestos, el borde izquierdo **es** el gesto de
 * "atrás", y no se le puede quitar: `setSystemGestureExclusionRects` está
 * limitado a 200 dp de altura por borde, así que un carril de altura completa
 * sencillamente no es excluible. Cualquier afordancia pegada a x=0 pierde esa
 * pelea. El carril de selección de filas empieza por tanto DESPUÉS de este
 * inset.
 *
 * En iOS no hay gesto de borde a nivel raíz (`MainViewController` monta un
 * `ComposeUIViewController` pelado, sin `UINavigationController`) y en
 * escritorio no hay gestos del sistema, así que ambos devuelven 0.
 */
@Composable
expect fun backGestureEdgeInset(): Dp
