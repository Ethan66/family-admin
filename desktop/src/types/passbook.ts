export interface PassbookPayload {
  memberId: number
  bank: string | null
  depositDate: string
  amount: number
  termYears: number
  interestRate: number
  totalInterest: number
  note: string | null
}

export interface PassbookRecord extends PassbookPayload {
  id: number
  memberName: string
  maturityDate: string
  totalAmount: number
  createdBy: number
}

export interface PassbookQuery {
  memberId?: number
  startDate?: string
  endDate?: string
  sort?: PassbookSort
  page: number
  pageSize: number
}

export type PassbookSort = 'DEPOSIT_DATE_DESC' | 'DEPOSIT_DATE_ASC' | 'MATURITY_DATE_ASC' | 'MATURITY_DATE_DESC'

export interface PassbookPage {
  records: PassbookRecord[]
  total: number
  page: number
  pageSize: number
  totalPrincipal: number
  totalAmount: number
}
