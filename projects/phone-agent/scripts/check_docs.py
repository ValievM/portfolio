#!/usr/bin/env python3
"""Checks the documentation: links, stale references, size budgets, layout.

Run from anywhere: python3 scripts/check_docs.py
Exit code 1 if any error is found.
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DOCS = REPO / 'docs'
AGENTS = REPO / 'AGENTS.md'
ROOT_ALLOWED = {'AGENTS.md', 'OWNER-TASKS.md'}  # the owner's one-screen task list lives in the root on purpose

AGENTS_BUDGET = 8 * 1024      # always loaded by Codex, keep it lean (hard limit of Codex is 32 KiB)
DOC_BUDGET = 14 * 1024        # one topic per file, roughly up to ~5k tokens
STALE_NAMES = ['TZ_MVP.md', 'ARCHITECTURE.md', 'DESIGN.md', 'DEV_STAGES.md']
PLANNED = {'docs/backend-notes.md'}
PLANNED_PREFIXES = ('docs/adr/',)
SKIP_DIRS = {'.git', 'build', '.gradle', 'node_modules', '.idea', '.air', '.prompts'}

LINK_RE = re.compile(r'\[[^\]]*\]\(([^)\s]+)\)')
TICK_PATH_RE = re.compile(r'`((?:docs/|\.\./)[\w./-]+\.md|AGENTS\.md)`')

errors, warnings = [], []


def slug(heading):
    h = heading.replace('`', '').strip().lower()
    h = re.sub(r'[^\w\- ]', '', h, flags=re.UNICODE)
    return h.replace(' ', '-')


def headings(path):
    out, fence = set(), False
    for ln in path.read_text(encoding='utf-8').split('\n'):
        if ln.lstrip().startswith('```'):
            fence = not fence
        if not fence:
            m = re.match(r'^#{1,6}\s+(.*)$', ln)
            if m:
                out.add(slug(m.group(1)))
    return out


def md_files():
    files = [AGENTS] if AGENTS.exists() else []
    files += sorted(DOCS.rglob('*.md'))
    return files


def rel(p):
    return p.relative_to(REPO)


def check_links(f):
    text = f.read_text(encoding='utf-8')
    fence = False
    for n, ln in enumerate(text.split('\n'), 1):
        if ln.lstrip().startswith('```'):
            fence = not fence
            continue
        if fence:
            continue
        for target in LINK_RE.findall(ln):
            if re.match(r'^(https?:|mailto:)', target):
                continue
            path, _, anchor = target.partition('#')
            dest = (f.parent / path).resolve() if path else f
            if not dest.exists():
                errors.append(f'{rel(f)}:{n}: broken link -> {target}')
                continue
            if anchor and dest.suffix == '.md' and anchor not in headings(dest):
                errors.append(f'{rel(f)}:{n}: missing anchor -> {target}')


def check_stale_and_ticks(f):
    text = f.read_text(encoding='utf-8')
    for n, ln in enumerate(text.split('\n'), 1):
        for name in STALE_NAMES:
            if name in ln:
                errors.append(f'{rel(f)}:{n}: stale reference to removed file {name}')
        for m in TICK_PATH_RE.finditer(ln):
            p = m.group(1)
            if p.startswith('../'):
                dest = (f.parent / p).resolve()
                ok = dest.exists()
            else:
                ok = (REPO / p).exists() or p in PLANNED or p.startswith(PLANNED_PREFIXES)
            if not ok:
                errors.append(f'{rel(f)}:{n}: `{p}` does not exist')


def table_cols(row):
    row = re.sub(r'`[^`]*`', 'X', row)
    return len(re.findall(r'(?<!\\)\|', row.strip().strip('|'))) + 1


def check_structure(f):
    lines = f.read_text(encoding='utf-8').split('\n')
    if sum(1 for ln in lines if ln.lstrip().startswith('```')) % 2:
        errors.append(f'{rel(f)}: unbalanced ``` code fences')
    fence, header_cols = False, None
    for n, ln in enumerate(lines, 1):
        if ln.lstrip().startswith('```'):
            fence = not fence
        if fence:
            continue
        if ln.strip().startswith('|'):
            cols = table_cols(ln)
            if header_cols is None:
                header_cols = cols
            elif cols != header_cols:
                errors.append(f'{rel(f)}:{n}: table row has {cols} columns, header has {header_cols}')
        else:
            header_cols = None
    h1 = [ln for ln in lines if re.match(r'^# ', ln)]
    if len(h1) != 1:
        errors.append(f'{rel(f)}: expected exactly one H1, found {len(h1)}')


def check_sizes():
    if AGENTS.exists():
        size = AGENTS.stat().st_size
        if size > AGENTS_BUDGET:
            errors.append(f'AGENTS.md is {size} bytes, budget {AGENTS_BUDGET} (always loaded, keep it lean)')
    for f in DOCS.rglob('*.md'):
        size = f.stat().st_size
        if size > DOC_BUDGET:
            errors.append(f'{rel(f)} is {size} bytes, budget {DOC_BUDGET}: split it')


def check_layout():
    for p in REPO.rglob('*.md'):
        if any(part in SKIP_DIRS for part in p.relative_to(REPO).parts):
            continue
        r = p.relative_to(REPO)
        if r.parts[0] != 'docs' and r.as_posix() not in ROOT_ALLOWED:
            errors.append(f'{r}: documentation must live in docs/ (the root allows only {sorted(ROOT_ALLOWED)})')


def check_index():
    index = DOCS / 'README.md'
    if not index.exists():
        errors.append('docs/README.md (index) is missing')
        return
    linked = set()
    for target in LINK_RE.findall(index.read_text(encoding='utf-8')):
        path = target.partition('#')[0]
        if path and not path.startswith(('http', 'mailto')):
            linked.add((index.parent / path).resolve())
    for f in DOCS.rglob('*.md'):
        if f.resolve() not in linked and f != index:
            errors.append(f'{rel(f)}: not linked from docs/README.md (orphan)')


def main():
    files = md_files()
    for f in files:
        check_links(f)
        check_stale_and_ticks(f)
        check_structure(f)
    check_sizes()
    check_layout()
    check_index()
    total = sum(f.stat().st_size for f in files)
    print(f'{len(files)} files, {total} bytes total')
    for w in warnings:
        print('WARN', w)
    for e in errors:
        print('ERROR', e)
    print('OK' if not errors else f'FAILED: {len(errors)} error(s)')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
