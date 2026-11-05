import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, type DefaultTheme } from 'vitepress'
import { tocRows } from './toc-rows'

// 문서 사이트 설정. 원본은 docs/ 의 마크다운이고, 같은 파일이 GitHub 위키에도 올라간다(.github/scripts/docs-wiki-sync.py)
// 메뉴는 손으로 적지 않는다: docs/README.md "분야" 목록과 각 폴더 README.md 목차의 링크 순서를 따른다

const DOCS = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const REPO = 'https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE'
const LINK = /\[([^\]]+)\]\(([^)\s#]+\.md)[^)]*\)/g

function links(rel: string): { text: string; file: string }[] {
  const base = path.posix.dirname(rel)
  const text = fs.readFileSync(path.join(DOCS, rel), 'utf-8')
  return [...text.matchAll(LINK)]
    .filter((m) => !/^[a-z]+:/i.test(m[2]))
    .map((m) => ({ text: m[1].replace(/^(\d+|부록)\.\s*/, ''), file: path.posix.normalize(path.posix.join(base, m[2])) }))
}

function url(file: string): string {
  return '/' + file.replace(/(^|\/)README\.md$/, '$1').replace(/\.md$/, '')
}

function sidebar(): DefaultTheme.SidebarItem[] {
  const items: DefaultTheme.SidebarItem[] = [
    { text: '시작', items: [{ text: '문서 안내', link: '/' }, { text: '문서 고치는 법', link: '/writing' }] },
  ]
  for (const field of links('README.md').filter((l) => l.file.endsWith('/README.md'))) {
    const pages = links(field.file).filter((l) => l.file !== field.file)
    items.push(pages.length
      ? { text: field.text, link: url(field.file), collapsed: true, items: pages.map((p) => ({ text: p.text, link: url(p.file) })) }
      : { text: field.text, link: url(field.file) })
  }
  return items
}

export default defineConfig({
  lang: 'ko-KR',
  title: 'Kidly 문서',
  description: '팀 공용 문서',
  // Pages 배포는 DOCS_BASE 로 저장소 이름 경로를 준다(docs-pages.yml). 로컬은 /
  base: process.env.DOCS_BASE || '/',
  cleanUrls: true,
  srcExclude: ['**/node_modules/**'],
  // 폴더의 README.md 를 그 폴더 첫 화면으로 쓴다. 레포 · 위키에서는 README.md 그대로 보인다
  rewrites: { 'README.md': 'index.md', ':dir/README.md': ':dir/index.md' },
  markdown: { config: (md) => md.use(tocRows) },
  transformPageData(pageData) {
    if (pageData.relativePath === 'index.md') {
      pageData.frontmatter.pageClass = 'page-home'
      pageData.frontmatter.outline = false
      pageData.frontmatter.prev = false
    } else if (pageData.relativePath.endsWith('/index.md')) {
      pageData.frontmatter.pageClass = 'page-toc'
    }
  },
  themeConfig: {
    sidebar: sidebar(),
    outline: { level: [2, 3], label: '이 문서의 내용' },
    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '검색', buttonAriaLabel: '검색' },
          modal: {
            noResultsText: '결과가 없다',
            resetButtonTitle: '지우기',
            footer: { selectText: '선택', navigateText: '이동', closeText: '닫기' },
          },
        },
      },
    },
    editLink: { pattern: `${REPO}/edit/develop/docs/:path`, text: '이 문서 고치기 (PR)' },
    socialLinks: [{ icon: 'github', link: REPO }],
    docFooter: { prev: '이전', next: '다음' },
    darkModeSwitchLabel: '테마',
    sidebarMenuLabel: '메뉴',
    returnToTopLabel: '맨 위로',
  },
})
