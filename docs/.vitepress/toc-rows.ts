import fs from 'node:fs'
import path from 'node:path'
import type MarkdownIt from 'markdown-it'

// README.md 의 목차 목록(`- [1. 제목](파일.md) · 설명`)을 사이트에서만 목차 행으로 그린다.
// 레포 · 위키에서는 평범한 목록으로 보인다.
// - 행: 번호 · 제목 · 설명 · 점선 · 읽는 시간. 어떻게 보일지는 목록 종류에 따라 CSS 가 정한다
//   docs/README.md 의 번호 목록 → toc-steps, 글머리 목록 → toc-fields, 폴더 README.md 의 목록 → toc-book
// - 목록 바로 위의 제목(## · ###)은 이름표로 바꿔 오른쪽 목차에서 뺀다

const CHARS_PER_MINUTE = 600

function minutes(file: string): number {
  try {
    const text = fs.readFileSync(file, 'utf-8').replace(/```[\s\S]*?```/g, '').replace(/\s+/g, '')
    return Math.max(1, Math.round(text.length / CHARS_PER_MINUTE))
  } catch {
    return 0
  }
}

function escape(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function tocRows(md: MarkdownIt) {
  md.core.ruler.push('kidly_toc_rows', (state) => {
    // rewrites 로 README.md 가 index.md 가 되므로 원래 파일 경로(realPath)를 본다
    const src: string | undefined = state.env?.realPath ?? state.env?.path
    if (!src || path.basename(src) !== 'README.md') return
    // docs/README.md 인지: 같은 폴더에 .vitepress 가 있다
    const isRoot = fs.existsSync(path.join(path.dirname(src), '.vitepress'))
    const tokens = state.tokens
    const lists: { token: (typeof tokens)[number]; used: boolean; count: number }[] = []

    for (let i = 0; i < tokens.length; i++) {
      const t = tokens[i]
      if (t.type === 'bullet_list_open' || t.type === 'ordered_list_open') {
        lists.push({ token: t, used: false, count: 0 })
        continue
      }
      if (t.type === 'bullet_list_close' || t.type === 'ordered_list_close') {
        const list = lists.pop()
        if (list?.used) {
          const kind = !isRoot ? 'toc-book' : list.token.type === 'ordered_list_open' ? 'toc-steps' : 'toc-fields'
          list.token.attrJoin('class', `toc-list ${kind}`)
        }
        continue
      }
      if (t.type !== 'inline' || !lists.length || !t.children?.length) continue
      const kids = t.children
      const href = kids[0].type === 'link_open' ? kids[0].attrGet('href') ?? '' : ''
      if (!/\.md(#.*)?$/.test(href) || /^[a-z]+:/i.test(href)) continue
      const close = kids.findIndex((k) => k.type === 'link_close')
      if (close < 0) continue

      const label = kids.slice(1, close).map((k) => k.content).join('')
      const m = label.match(/^(\d+|부록)\.\s*(.*)$/)
      const list = lists[lists.length - 1]
      list.count++
      // 번호 목록은 제목에 번호가 없으면 목록 순서를 번호로 쓴다
      const no = m ? m[1] : list.token.type === 'ordered_list_open' ? String(list.count) : ''
      const title = m ? m[2] : label
      const desc = kids.slice(close + 1).map((k) => k.content).join('').replace(/^\s*·\s*/, '').trim()
      const target = path.resolve(path.dirname(src), href.split('#')[0])
      const min = path.basename(target) === 'README.md' ? 0 : minutes(target)

      const open = kids[0]
      open.attrJoin('class', 'ti')
      const head = new state.Token('html_inline', '', 0)
      head.content = `<span class="ti-no">${escape(no)}</span><span class="ti-t">${escape(title)}</span>`
      const tail = new state.Token('html_inline', '', 0)
      tail.content = `<span class="ti-d">${escape(desc)}</span><span class="ti-dots"></span>`
        + `<span class="ti-m">${min ? `${min}분` : ''}</span>`
      t.children = [open, head, tail, kids[close]]
      list.used = true
    }

    // 목차 목록 바로 위의 제목은 이름표로
    for (let i = 0; i + 3 < tokens.length; i++) {
      const h = tokens[i]
      const next = tokens[i + 3]
      if (h.type === 'heading_open' && (h.tag === 'h2' || h.tag === 'h3')
        && /^(bullet|ordered)_list_open$/.test(next.type) && (next.attrGet('class') ?? '').includes('toc-list')) {
        h.tag = 'div'
        tokens[i + 2].tag = 'div'
        h.attrSet('class', 'toc-label')
      }
    }
  })
}
