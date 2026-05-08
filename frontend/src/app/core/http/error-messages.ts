import { ApiError } from './error.interceptor';

/**
 * Translates Docker daemon's verbose conflict messages into short,
 * user-readable sentences. Falls back to the original API message if
 * we can't recognize the pattern.
 *
 * The daemon often returns things like:
 *   conflict: unable to delete 9a2bd816daba (cannot be forced) - image
 *   is being used by running container 3328c0fd00fc
 * which is unhelpful to a non-Docker user.
 */
export function humanizeDeleteError(err: unknown, resource: 'imagen' | 'volumen' | 'red'): string {
  const raw = err instanceof ApiError ? err.message : String(err ?? '');
  const lower = raw.toLowerCase();

  // Image / volume / network "in use" rejections
  if (lower.includes('is being used by running container')) {
    return `La ${resource} está siendo usada por un contenedor en ejecución. Detenlo antes de eliminarla.`;
  }
  if (lower.includes('is being used by') || lower.includes('still in use') || lower.includes('volume is in use')) {
    return `La ${resource} está siendo usada por uno o más contenedores. Quítala de ellos antes de eliminarla.`;
  }
  if (lower.includes('has active endpoints') || lower.includes('network has active endpoints')) {
    return `La red tiene contenedores conectados. Desconéctalos antes de eliminarla.`;
  }
  if (lower.includes('not found')) {
    return `La ${resource} ya no existe.`;
  }

  // Generic fallback — strip the noisy "conflict:" prefix if present.
  const trimmed = raw.replace(/^(conflict|error response from daemon):\s*/i, '');
  return trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
}
