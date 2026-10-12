import { afterEach, describe, expect, it, vi } from 'vitest'
import { invoke } from '@tauri-apps/api/core'
import { recyclePhoto } from '@/api/resources'
import type { ResourceIndex } from '@/types/resource'

vi.mock('@tauri-apps/api/core', () => ({ invoke: vi.fn() }))

describe('photo deletion', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('asks the desktop runtime to recycle the selected photo in its family', async () => {
    vi.stubGlobal('__TAURI_INTERNALS__', {})
    const tempIndex = { familyId: 12, resources: [] } as unknown as ResourceIndex
    vi.mocked(invoke).mockResolvedValue(tempIndex)

    await expect(recyclePhoto(12, 'photo-1')).resolves.toBe(tempIndex)

    expect(invoke).toHaveBeenCalledWith('recycle_photo', {
      familyId: 12,
      resourceId: 'photo-1',
    })
  })
})
