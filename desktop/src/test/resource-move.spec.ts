import { afterEach, describe, expect, it, vi } from 'vitest'
import { invoke } from '@tauri-apps/api/core'
import { movePhotos } from '@/api/resources'
import type { ResourceIndex } from '@/types/resource'

vi.mock('@tauri-apps/api/core', () => ({ invoke: vi.fn() }))

describe('photo move command', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('passes family, photo IDs and destination to the desktop backend', async () => {
    vi.stubGlobal('__TAURI_INTERNALS__', {})
    const index = { familyId: 12, resources: [] } as unknown as ResourceIndex
    vi.mocked(invoke).mockResolvedValue(index)

    await expect(movePhotos(12, ['photo-1', 'photo-2'], 'D:/photos', '旅行')).resolves.toBe(index)

    expect(invoke).toHaveBeenCalledWith('move_photos', {
      familyId: 12,
      resourceIds: ['photo-1', 'photo-2'],
      destinationParent: 'D:/photos',
      newFolderName: '旅行',
    })
  })
})
