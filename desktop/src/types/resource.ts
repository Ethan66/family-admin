export type ResourceKind = 'PHOTO' | 'MOVIE' | 'NOTE' | 'EXAM'

export interface PhotoDetails {
  title: string
  album: string
  date: string
  description: string
  categories: string[]
  rating: number | null
}

export interface MovieDetails {
  title: string
  movieType: string
  year: number | null
  director: string
  minutes: number | null
  rating: number | null
  status: string
  progress: number
  description: string
}

export interface ExamDetails {
  title: string
  subject: string
  grade: string
  date: string
  fullScore: number
  score: number | null
  status: string
  note: string
}

export type ResourceMetadata =
  | { kind: 'PHOTO'; details: PhotoDetails }
  | { kind: 'MOVIE'; details: MovieDetails }
  | { kind: 'EXAM'; details: ExamDetails }

export interface ResourceEntry {
  id: string
  kind: ResourceKind
  path: string
  name: string
  extension: string
  size: number
  modifiedAt: string
  capturedAt?: string
  imageWidth?: number
  imageHeight?: number
}

export interface ScanWarning {
  path: string
  reason: string
}

export interface ResourceRoots {
  photos: string[]
  movies: string[]
  notes: string[]
  exams: string[]
}

export interface ResourceIndex {
  schemaVersion: number
  familyId: number
  roots: ResourceRoots
  lastScannedAt: string | null
  resources: ResourceEntry[]
  warnings: ScanWarning[]
  photoDirectories: string[]
  metadataById: Record<string, ResourceMetadata>
}

export interface PreviewData {
  mimeType: string
  base64: string
}
