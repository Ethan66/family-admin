import { describe, expect, it } from 'vitest'
import { buildPhotoRows } from '@/utils/photoWall'
import type { ResourceEntry } from '@/types/resource'

function photo(id: string, imageWidth?: number, imageHeight?: number): ResourceEntry {
  return {
    id, kind: 'PHOTO', path: `D:/photos/${id}.jpg`, name: `${id}.jpg`, extension: '.jpg',
    size: 1, modifiedAt: '2026-10-23T00:00:00Z', imageWidth, imageHeight,
  }
}

describe('justified photo wall', () => {
  it('keeps original ratios and fills every complete row exactly', () => {
    const resources = [photo('wide', 200, 100), photo('square', 100, 100), photo('tall', 100, 200), photo('normal', 100, 100), photo('last', 100, 100)]

    const rows = buildPhotoRows(resources, 600, 140, 10)

    expect(rows).toHaveLength(2)
    expect(rows[0].items.map((item) => item.resource.id)).toEqual(['wide', 'square', 'tall', 'normal'])
    expect(rows[0].items.reduce((sum, item) => sum + item.width, 30)).toBeCloseTo(600)
    expect(rows[0].items[0].width / rows[0].height).toBeCloseTo(2)
    expect(rows[0].items[2].width / rows[0].height).toBeCloseTo(0.5)
    expect(rows[1].height).toBe(140)
    expect(rows[1].items[0].width).toBe(140)
  })

  it('uses a safe fallback ratio for old indexes and fits a panoramic last row', () => {
    const rows = buildPhotoRows([photo('old'), photo('panorama', 400, 100)], 300, 140, 10)

    expect(rows[0].items[0].width / rows[0].height).toBeCloseTo(4 / 3)
    expect(rows.at(-1)?.items.at(-1)?.width).toBeLessThanOrEqual(300)
  })

  it('uses the zoom height when packing rows', () => {
    const resources = [photo('one', 200, 100), photo('two', 200, 100)]

    expect(buildPhotoRows(resources, 600, 140, 10)).toHaveLength(1)
    expect(buildPhotoRows(resources, 600, 160, 10)[0].height).toBeCloseTo(147.5)
  })
})
