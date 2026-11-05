// DOM-only adapter. No approve/reject/submit/save actions or API writes.
var op = arguments[0], kind = arguments[1], wanted = arguments[2];
function visible(e) {
    if (!e || !e.getClientRects().length) return false;
    for (var n = e; n && n.nodeType === 1; n = n.parentElement) {
        var s = getComputedStyle(n);
        if (s.display === 'none' || s.visibility === 'hidden') return false;
    }
    return true;
}
function key(s) { return String(s || '').toLowerCase().replace(/[^a-z0-9]/g, ''); }
function text(e) { return (e.innerText || '').replace(/\s+/g, ' ').trim(); }
function all(root, css) { return Array.prototype.slice.call(root.querySelectorAll(css)); }
function canonical(s) {
    var k = key(s);
    if (['requestno','referenceno','refno','requestnumber'].indexOf(k) >= 0) return 'refno';
    if (k === 'approvedatetime') return 'approveddate';
    return k;
}
function columns(t) {
    var heads = all(t, 'thead tr').filter(visible);
    var cells = heads.length ? all(heads[heads.length-1], 'th,td') : all(t, 'tr:first-child th');
    var names = cells.map(function(c) { return canonical(text(c) || c.getAttribute('data-title-text')); });
    if (!names.length) {
        var first = t.querySelector('tbody tr') || t.querySelector('tr');
        if (first) names = all(first, 'td').map(function(c) {
            return canonical(c.getAttribute('data-title-text') || c.getAttribute('title'));
        });
    }
    return names;
}
function table(type) {
    var matches = all(document, 'table').filter(function(t) {
        if (!visible(t)) return false;
        var h = columns(t);
        return type === 'requests' ? h.indexOf('refno') >= 0 && (h.indexOf('projectname') >= 0 || h.indexOf('projectid') >= 0) :
            type === 'steps' ? h.indexOf('step') >= 0 && h.indexOf('description') >= 0 :
            h.indexOf('approver') >= 0 && h.indexOf('status') >= 0 && h.indexOf('approveddate') >= 0;
    });
    if (type === 'approvals') {
        var modal = matches.filter(function(t) { return t.closest('.modal,[role="dialog"]'); });
        if (modal.length) matches = modal;
    }
    if (matches.length > 1) throw new Error('More than one visible ' + type + ' table; cannot select safely.');
    return matches.length ? matches[0] : null;
}
function rows(t) {
    var names = columns(t), result = [];
    all(t, 'tr').forEach(function(tr) {
        if (!visible(tr) || tr.closest('table') !== t || tr.querySelector('th')) return;
        var cells = Array.prototype.slice.call(tr.children).filter(function(c) { return c.tagName === 'TD'; });
        if (!cells.length) return;
        if (cells.length === 1 && Number(cells[0].getAttribute('colspan')) > 1) {
            if (/no (data|records|results)|not found|ไม่มีข้อมูล|ไม่พบข้อมูล/i.test(text(tr))) return;
            throw new Error('Unrecognized table placeholder: ' + text(tr));
        }
        if (cells.length !== names.length) throw new Error('Table columns changed; refusing a shifted row.');
        var values = {};
        names.forEach(function(name, i) { if (name) values[name] = text(cells[i]); });
        if (Object.keys(values).every(function(k) { return !values[k]; })) return;
        result.push({values:values, element:tr});
    });
    return result;
}
function pager(t) {
    for (var p=t.parentElement; p && p !== document.body; p=p.parentElement) {
        var candidates=all(p,'.pagination,ul.ng-table-pagination').filter(visible);
        if (candidates.length > 1) throw new Error('Ambiguous pagination container.');
        if (candidates.length === 1) return candidates[0];
        if (p.classList.contains('box')) break;
    }
    return null;
}
function disabled(e) {
    return e.disabled || e.getAttribute('aria-disabled') === 'true' || !!e.closest('.disabled');
}
function next(t) {
    var p=pager(t);
    if (!p) return null;
    var buttons=all(p,'a,button').filter(visible);
    var matches=buttons.filter(function(a) {
        return a.getAttribute('ng-switch-when') === 'next' || a.getAttribute('data-ng-switch-when') === 'next'
            || /^(next|next page|›|»|>)$/i.test(text(a)) || /^next/i.test(a.getAttribute('aria-label') || '');
    });
    if (matches.length > 1) throw new Error('Multiple Next buttons.');
    if (matches.length) return disabled(matches[0]) ? null : matches[0];
    var active = p.querySelector('.active');
    var current = active ? Number(text(active)) : NaN;
    var later = buttons.filter(function(a) { return /^\d+$/.test(text(a)) && Number(text(a)) > current && !disabled(a); });
    if (later.length) {
        later.sort(function(a,b) { return Number(text(a))-Number(text(b)); });
        if (Number(text(later[0])) !== current+1) throw new Error('Cannot locate the next consecutive page.');
        return later[0];
    }
    if (!active && buttons.some(function(a) { return /^\d+$/.test(text(a)) && Number(text(a)) > 1; }))
        throw new Error('Cannot establish the current page.');
    return null;
}
if (op === 'idle') {
    if (document.readyState !== 'complete') return false;
    if (all(document,'.swal2-image,.blockUI.blockOverlay,.loading-overlay').some(visible)) return false;
    try {
        if (window.angular) {
            var injector=window.angular.element(document.body).injector();
            if (injector && injector.get('$http').pendingRequests.length) return false;
        }
    } catch (ignore) { /* Non-Angular pages rely on visible loading and settling. */ }
    return true;
}
if (op === 'signature') return document.body.innerText;
if (op === 'section') {
    var headings=all(document,'h1,h2,h3,h4').filter(function(h) { return visible(h) && text(h) === 'Activity/Milestone'; });
    if (headings.length !== 1) return null;
    var box=headings[0].closest('.box');
    if (box) return box.querySelector('[data-widget="collapse"]') || headings[0];
    return headings[0];
}
if (op === 'approvalTab') {
    var links=all(document,'a,button[role="tab"]').filter(function(a) { return visible(a) && text(a) === 'Approval Line'; });
    var modalLinks=links.filter(function(a) { return a.closest('.modal,[role="dialog"]'); });
    if (modalLinks.length) links=modalLinks;
    if (links.length > 1) throw new Error('More than one Approval Line tab.');
    return links.length ? links[0] : null;
}
var t=table(kind);
if (op === 'exists') return !!t;
if (!t) return null;
if (op === 'snapshot') return rows(t).map(function(r) { return r.values; });
if (op === 'next') return next(t);
if (op === 'link') {
    var column=kind === 'requests' ? 'refno' : 'step';
    var matches=rows(t).filter(function(r) { return r.values[column] === wanted; });
    if (matches.length > 1) throw new Error('Duplicate ' + column + ' on this page.');
    if (!matches.length) return null;
    var index=columns(t).indexOf(column);
    var links=all(matches[0].element.children[index],'a').filter(function(a) { return visible(a) && text(a) === wanted; });
    if (links.length !== 1) throw new Error('Expected one detail link for ' + wanted);
    return links[0];
}
throw new Error('Unknown DOM operation: ' + op);
