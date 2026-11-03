' Inicio de Facst Play TV.
' Zonas con foco: "hero" (botones de la portada), "rows" (filas) y "rail" (menú lateral).

sub init()
    m.backdrop = m.top.findNode("backdrop")
    m.placeholder = m.top.findNode("placeholder")
    m.badge = m.top.findNode("badge")
    m.badgeText = m.top.findNode("badgeText")
    m.kicker = m.top.findNode("kicker")
    m.title = m.top.findNode("title")
    m.meta = m.top.findNode("meta")
    m.plot = m.top.findNode("plot")
    m.btns = m.top.findNode("btns")
    m.dots = m.top.findNode("dots")
    m.rows = m.top.findNode("rows")
    m.status = m.top.findNode("status")
    m.clock = m.top.findNode("clock")
    m.dateLabel = m.top.findNode("date")
    m.accPill = m.top.findNode("accPill")
    m.accDot = m.top.findNode("accDot")
    m.accText = m.top.findNode("accText")
    m.railItemsGroup = m.top.findNode("railItems")
    m.infoTimer = m.top.findNode("infoTimer")
    m.rotTimer = m.top.findNode("rotTimer")
    m.tick = m.top.findNode("tick")

    m.zone = "hero"
    m.prevZone = "hero"
    m.lastPos = invalid
    m.btnIndex = 0
    m.railIndex = 1
    m.server = invalid
    m.featList = []
    m.featIndex = 0
    m.rowData = []
    m.shown = invalid
    m.infoCache = {}
    m.infoTasks = {}
    m.pendingInfo = invalid
    m.homeTask = invalid
    m.dialog = invalid

    buildRail()
    buildButtons()
    m.rows.observeField("rowItemFocused", "onRowFocus")
    m.rows.observeField("rowItemSelected", "onRowSelect")
    m.infoTimer.observeField("fire", "onInfoTimer")
    m.rotTimer.observeField("fire", "onRotate")
    m.tick.observeField("fire", "refreshInfo")
    m.tick.control = "start"
    refreshInfo()
    showEmptyHero()
    buildRows()
    loadHome()
end sub

sub focusScreen()
    refreshInfo()
    ' Al volver de otra pantalla se actualizan "Continuar viendo", "Mi lista" y los canales vistos.
    buildRows()
    applyFocus()
end sub

' ----------------------------------------------------------------- datos del servidor

sub loadHome()
    cached = cacheGet("home_rows", 900)
    if cached <> invalid
        onHomeData(cached.data)
        return
    end if
    m.status.text = "Cargando lo nuevo…"
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "home", tag: "home" }
    t.observeField("response", "onHomeLoaded")
    m.homeTask = t
    t.control = "RUN"
end sub

sub onHomeLoaded(event as Object)
    resp = event.getData()
    m.homeTask = invalid
    if resp = invalid or resp.ok <> true
        m.status.text = ""
        if m.rowData.Count() = 0
            msg = "No se pudo cargar el inicio. Revisa tu internet e inténtalo otra vez."
            if resp <> invalid and toS(resp.error) <> "" then msg = toS(resp.error)
            m.status.text = msg
        end if
        return
    end if
    data = { movies: resp.movies, series: resp.series, live: resp.live, liveName: toS(resp.liveName) }
    holder = CreateObject("roSGNode", "Node")
    holder.addFields({ data: data })
    cachePut("home_rows", holder)
    onHomeData(data)
end sub

sub onHomeData(data as Object)
    m.status.text = ""
    m.server = data
    m.featList = []
    for each e in data.movies
        if m.featList.Count() < 5 then m.featList.Push(e)
    end for
    if m.featList.Count() = 0
        for each e in data.series
            if m.featList.Count() < 5 then m.featList.Push(e)
        end for
    end if
    m.featIndex = 0
    buildDots()
    buildRows()
    if m.zone = "hero" then showFeatured()
    if m.featList.Count() > 1 then m.rotTimer.control = "start"
    ' Las fichas de las destacadas se piden de una vez, así la portada cambia sin esperas.
    for each e in m.featList
        if e.kind = "movie" then requestInfo(e)
    end for
end sub

' ----------------------------------------------------------------- filas

sub buildRows()
    previous = m.lastPos
    rows = []

    cont = []
    for each c in loadList("continue")
        kind = toS(c.kind)
        pct = 0.0
        if toI(c.dur) > 0 then pct = toI(c.pos) / toI(c.dur)
        cont.Push({ kind: "continue", what: kind, id: toS(c.id), name: toS(c.name), icon: toS(c.icon), ext: toS(c.ext), pos: toI(c.pos), dur: toI(c.dur), pct: pct, raw: c })
    end for
    if cont.Count() > 0 then rows.Push({ title: "Continuar viendo", style: "poster", items: cont })

    if m.server <> invalid
        if m.server.movies.Count() > 0 then rows.Push({ title: "Películas nuevas", style: "poster", items: m.server.movies })
        if m.server.series.Count() > 0 then rows.Push({ title: "Series nuevas", style: "poster", items: m.server.series })
    end if

    recent = []
    for each c in recentLive()
        recent.Push({ kind: "live", id: toS(c.id), name: toS(c.name), icon: toS(c.icon) })
    end for
    if recent.Count() > 0
        rows.Push({ title: "Canales que viste", style: "channel", items: recent })
    else if m.server <> invalid and m.server.live.Count() > 0
        t = "En vivo"
        if m.server.liveName <> "" then t = "En vivo · " + m.server.liveName
        rows.Push({ title: t, style: "channel", items: m.server.live })
    end if

    mine = []
    for each f in loadList(favKey("movie"))
        mine.Push({ kind: "movie", id: toS(f.id), name: toS(f.name), icon: toS(f.icon), ext: toS(f.ext) })
    end for
    for each f in loadList(favKey("series"))
        mine.Push({ kind: "series", id: toS(f.id), name: toS(f.name), icon: toS(f.icon) })
    end for
    if mine.Count() > 0 then rows.Push({ title: "Mi lista", style: "poster", items: mine })

    root = CreateObject("roSGNode", "ContentNode")
    sizes = []
    heights = []
    m.rowData = []
    for each r in rows
        row = root.CreateChild("ContentNode")
        row.title = r.title
        list = []
        for each e in r.items
            pct = 0.0
            if e.pct <> invalid then pct = e.pct
            list.Push({ title: toS(e.name), HDPosterUrl: thumbUrl(toS(e.icon)), xStyle: r.style, xProgress: pct })
        end for
        row.Update({ children: list }, true)
        if r.style = "channel"
            sizes.Push([190, 112])
            heights.Push(112)
        else
            sizes.Push([124, 186])
            heights.Push(186)
        end if
        m.rowData.Push(r.items)
    end for
    m.rows.rowItemSize = sizes
    m.rows.rowHeights = heights
    m.rows.content = root
    m.rows.visible = (m.rowData.Count() > 0)
    ' Se vuelve a dejar el foco donde estaba (solo si la persona ya había entrado a las filas).
    if previous <> invalid and m.rowData.Count() > 0
        r = previous[0]
        c = previous[1]
        if r >= m.rowData.Count() then r = m.rowData.Count() - 1
        if c >= m.rowData[r].Count() then c = m.rowData[r].Count() - 1
        if c < 0 then c = 0
        m.rows.jumpToRowItem = [r, c]
    end if
    if m.rowData.Count() = 0 and m.zone = "rows" then setZone("hero")
end sub

function rowItem(at as Dynamic) as Dynamic
    if at = invalid then return invalid
    if Type(at) <> "roArray" then return invalid
    if at.Count() < 2 then return invalid
    r = at[0]
    c = at[1]
    if r < 0 or r >= m.rowData.Count() then return invalid
    if c < 0 or c >= m.rowData[r].Count() then return invalid
    return m.rowData[r][c]
end function

sub onRowFocus()
    at = m.rows.rowItemFocused
    if rowItem(at) <> invalid then m.lastPos = [at[0], at[1]]
    if m.zone <> "rows" then return
    e = rowItem(at)
    if e = invalid then return
    showItem(e, false)
end sub

sub onRowSelect()
    at = m.rows.rowItemSelected
    e = rowItem(at)
    if e = invalid then return
    kind = e.kind
    if kind = "continue"
        m.top.event = { type: "playEntry", entry: e.raw }
    else if kind = "movie"
        m.top.event = { type: "openMovie", item: { id: e.id, name: e.name, icon: e.icon, ext: toS(e.ext) } }
    else if kind = "series"
        m.top.event = { type: "openSeries", item: { id: e.id, name: e.name, icon: e.icon, ext: "" } }
    else if kind = "live"
        items = []
        for each x in m.rowData[at[0]]
            items.Push({ id: x.id, name: x.name, icon: x.icon, ext: "" })
        end for
        m.top.event = { type: "playLive", items: items, index: at[1] }
    end if
end sub

' ----------------------------------------------------------------- portada (lo destacado o lo enfocado)

sub showEmptyHero()
    m.badge.visible = false
    m.badgeText.visible = false
    m.kicker.text = ""
    m.title.text = "Bienvenido a Facst Play TV"
    m.meta.text = "TV en vivo  ·  Películas  ·  Series"
    m.plot.text = ""
    m.backdrop.uri = ""
    layoutHero()
end sub

sub showFeatured()
    if m.featList.Count() = 0
        showEmptyHero()
        return
    end if
    if m.featIndex >= m.featList.Count() then m.featIndex = 0
    showItem(m.featList[m.featIndex], true)
    updateDots()
    updateButtons()
end sub

sub showItem(e as Object, featured as Boolean)
    m.shown = e
    kind = e.kind
    if kind = "continue" then kind = e.what
    badge = "DESTACADO"
    if not featured
        if e.kind = "continue"
            badge = "CONTINUAR"
        else if kind = "movie"
            badge = "PELÍCULA"
        else if kind = "series"
            badge = "SERIE"
        else if kind = "live"
            badge = "EN VIVO"
        else
            badge = "EPISODIO"
        end if
    end if
    m.badgeText.text = badge
    if badge = "EN VIVO"
        m.badge.uri = "pkg:/images/pill_cyan.9.png"
    else
        m.badge.uri = "pkg:/images/pill_red.9.png"
    end if
    m.badge.visible = true
    m.badgeText.visible = true
    m.kicker.text = UCase(toS(e.cat))
    m.title.text = toS(e.name)
    m.plot.text = toS(e.plot)
    m.meta.text = metaLine(e, invalid)
    setBackdrop(toS(e.backdrop))
    if e.kind = "continue"
        m.meta.text = "Te quedaste en " + fmtClock(e.pos) + " de " + fmtClock(e.dur)
        if e.what = "movie" then requestLater(e)
    else if kind = "live"
        m.meta.text = "Canal en vivo"
        m.plot.text = "Pulsa OK para verlo."
    else if kind = "movie"
        cached = m.infoCache[e.id]
        if cached <> invalid
            applyInfo(e, cached)
        else if featured
            requestInfo(e)
        else
            requestLater(e)
        end if
    end if
    layoutHero()
end sub

function metaLine(e as Object, info as Dynamic) as String
    parts = []
    year = toS(e.year)
    genre = toS(e.genre)
    rating = toS(e.rating)
    dur = ""
    if info <> invalid
        if toS(info.year) <> "" then year = toS(info.year)
        if toS(info.genre) <> "" then genre = toS(info.genre)
        if toS(info.rating) <> "" then rating = toS(info.rating)
        dur = toS(info.duration)
    end if
    if year <> "" then parts.Push(year)
    if dur <> "" then parts.Push(dur)
    if genre <> "" then parts.Push(genre)
    if rating <> "" and rating <> "0" then parts.Push("Puntuación " + rating)
    out = ""
    for each p in parts
        if out <> "" then out = out + "   ·   "
        out = out + p
    end for
    return out
end function

sub setBackdrop(uri as String)
    m.backdrop.uri = uri
    m.placeholder.visible = (uri = "")
end sub

sub applyInfo(e as Object, info as Object)
    if m.shown = invalid then return
    if m.shown.id <> e.id then return
    if toS(info.plot) <> "" then m.plot.text = toS(info.plot)
    if e.kind <> "continue" then m.meta.text = metaLine(e, info)
    if toS(info.backdrop) <> "" then setBackdrop(toS(info.backdrop))
    layoutHero()
end sub

' Ubica título, datos, sinopsis y botones uno debajo del otro según cuánto ocupa cada texto.
sub layoutHero()
    y = 160
    th = m.title.boundingRect().height
    if th < 40 then th = 50
    y = y + th + 4
    m.meta.translation = [0, y]
    y = y + 30
    m.plot.translation = [0, y]
    ph = 0
    if m.plot.text <> "" then ph = m.plot.boundingRect().height
    y = y + ph + 20
    if y < 300 then y = 300
    if y > 352 then y = 352
    m.btns.translation = [0, y]
    m.dots.translation = [452, y + 21]
end sub

' ----------------------------------------------------------------- fichas de películas (sinopsis e imagen de fondo)

sub requestLater(e as Object)
    m.pendingInfo = e
    m.infoTimer.control = "stop"
    m.infoTimer.control = "start"
end sub

sub onInfoTimer()
    e = m.pendingInfo
    m.pendingInfo = invalid
    if e = invalid then return
    if m.shown = invalid or m.shown.id <> e.id then return
    requestInfo(e)
end sub

sub requestInfo(e as Object)
    id = toS(e.id)
    if id = "" then return
    cached = m.infoCache[id]
    if cached <> invalid
        applyInfo(e, cached)
        return
    end if
    if m.infoTasks.DoesExist(id) then return
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "vodinfo", id: id, tag: id }
    t.observeField("response", "onInfo")
    m.infoTasks[id] = t
    t.control = "RUN"
end sub

sub onInfo(event as Object)
    resp = event.getData()
    if resp = invalid then return
    id = toS(resp.tag)
    m.infoTasks.Delete(id)
    if resp.ok <> true then return
    m.infoCache[id] = resp.info
    if m.shown <> invalid and m.shown.id = id then applyInfo(m.shown, resp.info)
end sub

' ----------------------------------------------------------------- carrusel de destacadas

sub buildDots()
    while m.dots.getChildCount() > 0
        m.dots.removeChildIndex(0)
    end while
    if m.featList.Count() < 2 then return
    for i = 0 to m.featList.Count() - 1
        d = m.dots.createChild("Rectangle")
        d.height = 5
    end for
    updateDots()
end sub

sub updateDots()
    n = m.dots.getChildCount()
    x = 0
    for i = 0 to n - 1
        d = m.dots.getChild(i)
        if i = m.featIndex
            d.width = 30
            d.color = "0x8B3DF0FF"
        else
            d.width = 9
            d.color = "0x3A3550FF"
        end if
        d.translation = [x, 0]
        x = x + d.width + 7
    end for
    m.dots.visible = (m.zone = "hero")
end sub

sub onRotate()
    if m.zone <> "hero" or not m.top.visible then return
    if m.featList.Count() < 2 then return
    m.featIndex = (m.featIndex + 1) mod m.featList.Count()
    showFeatured()
end sub

' ----------------------------------------------------------------- botones de la portada

sub buildButtons()
    m.btnDefs = [
        { id: "play", label: "Ver ahora", icon: "pkg:/images/ic_play.png", w: 156 },
        { id: "info", label: "Más información", icon: "pkg:/images/ic_info.png", w: 200 },
        { id: "fav", label: "", icon: "pkg:/images/ic_plus.png", w: 48 }
    ]
    m.btnNodes = []
    x = 0
    for each d in m.btnDefs
        g = m.btns.createChild("Group")
        g.translation = [x, 0]
        bg = g.createChild("Poster")
        bg.width = d.w
        bg.height = 48
        icon = g.createChild("Poster")
        icon.uri = d.icon
        icon.width = 22
        icon.height = 22
        label = g.createChild("Label")
        label.text = d.label
        label.height = 48
        label.vertAlign = "center"
        f = CreateObject("roSGNode", "Font")
        f.uri = "pkg:/fonts/Poppins-Bold.ttf"
        f.size = 16
        label.font = f
        if d.label = ""
            icon.translation = [13, 13]
        else
            icon.translation = [18, 13]
            label.translation = [48, 0]
            label.width = d.w - 56
        end if
        m.btnNodes.Push({ group: g, bg: bg, icon: icon, label: label, w: d.w })
        x = x + d.w + 12
    end for
    m.ring = m.btns.createChild("Poster")
    m.ring.uri = "pkg:/images/ring_red.9.png"
    updateButtons()
end sub

sub updateButtons()
    fav = false
    e = currentFeatured()
    if e <> invalid then fav = isFavorite(e.kind, toS(e.id))
    for i = 0 to m.btnNodes.Count() - 1
        b = m.btnNodes[i]
        if i = 0
            b.bg.uri = "pkg:/images/btn_white.9.png"
            b.icon.blendColor = "0x07060FFF"
            b.label.color = "0x07060FFF"
        else
            b.bg.uri = "pkg:/images/btn_glass.9.png"
            b.icon.blendColor = "0xFFFFFFFF"
            b.label.color = "0xF3F1FAFF"
        end if
        if i = 2
            if fav
                b.icon.uri = "pkg:/images/ic_check.png"
            else
                b.icon.uri = "pkg:/images/ic_plus.png"
            end if
        end if
    end for
    focused = m.btnNodes[m.btnIndex]
    m.ring.translation = [focused.group.translation[0] - 5, -5]
    m.ring.width = focused.w + 10
    m.ring.height = 58
    m.ring.visible = (m.zone = "hero")
    m.btns.visible = (m.zone = "hero" and m.featList.Count() > 0)
end sub

function currentFeatured() as Dynamic
    if m.featList.Count() = 0 then return invalid
    if m.featIndex >= m.featList.Count() then return invalid
    return m.featList[m.featIndex]
end function

sub pressButton()
    e = currentFeatured()
    if e = invalid then return
    id = m.btnDefs[m.btnIndex].id
    item = { id: toS(e.id), name: toS(e.name), icon: toS(e.icon), ext: toS(e.ext) }
    if id = "play"
        if e.kind = "series"
            m.top.event = { type: "openSeries", item: item }
            return
        end if
        if item.ext = "" then item.ext = "mp4"
        cached = m.infoCache[item.id]
        if cached <> invalid and toS(cached.ext) <> "" then item.ext = toS(cached.ext)
        saved = findContinue("movie", item.id)
        secs = 0
        if saved <> invalid and toI(saved.pos) >= 5 then secs = toI(saved.pos)
        m.top.event = { type: "play", mode: "movie", items: [item], index: 0, start: secs }
    else if id = "info"
        if e.kind = "series"
            m.top.event = { type: "openSeries", item: item }
        else
            m.top.event = { type: "openMovie", item: item }
        end if
    else if id = "fav"
        toggleFavorite(e.kind, item)
        updateButtons()
        buildRows()
    end if
end sub

' ----------------------------------------------------------------- menú de arriba (pestañas)

sub buildRail()
    m.railDefs = [
        { id: "search", label: "", icon: "pkg:/images/ic_search.png" },
        { id: "home", label: "Inicio", icon: "" },
        { id: "live", label: "En vivo", icon: "" },
        { id: "movie", label: "Películas", icon: "" },
        { id: "series", label: "Series", icon: "" },
        { id: "continue", label: "Seguir viendo", icon: "" },
        { id: "settings", label: "", icon: "pkg:/images/ic_settings.png" }
    ]
    m.railNodes = []
    x = 0
    for i = 0 to m.railDefs.Count() - 1
        d = m.railDefs[i]
        g = m.railItemsGroup.createChild("Group")
        g.translation = [x, 0]
        sel = g.createChild("Poster")
        sel.uri = "pkg:/images/rail_sel.9.png"
        sel.height = 40
        icon = invalid
        label = invalid
        w = 44
        if d.icon <> ""
            icon = g.createChild("Poster")
            icon.uri = d.icon
            icon.translation = [11, 9]
            icon.width = 22
            icon.height = 22
        else
            label = g.createChild("Label")
            label.text = d.label
            label.height = 40
            label.vertAlign = "center"
            f = CreateObject("roSGNode", "Font")
            f.uri = "pkg:/fonts/Poppins-Medium.ttf"
            f.size = 16
            label.font = f
            tw = label.boundingRect().width
            if tw < 30 then tw = Len(d.label) * 9
            w = tw + 36
            label.translation = [18, 0]
            label.width = tw + 2
        end if
        sel.width = w
        m.railNodes.Push({ sel: sel, icon: icon, label: label })
        x = x + w + 6
    end for
    updateRail()
end sub

sub updateRail()
    open = (m.zone = "rail")
    for i = 0 to m.railNodes.Count() - 1
        n = m.railNodes[i]
        isHome = (m.railDefs[i].id = "home")
        lit = false
        if open
            n.sel.visible = (i = m.railIndex)
            if i = m.railIndex
                n.sel.uri = "pkg:/images/rail_sel.9.png"
                lit = true
            end if
        else
            n.sel.visible = isHome
            n.sel.uri = "pkg:/images/rail_focus.9.png"
            lit = isHome
        end if
        color = "0xB4AECBFF"
        if lit then color = "0xFFFFFFFF"
        if n.label <> invalid then n.label.color = color
        if n.icon <> invalid then n.icon.blendColor = color
    end for
end sub

sub pressRail()
    id = m.railDefs[m.railIndex].id
    setZone("hero")
    if id <> "home" then m.top.event = { type: "open", target: id }
end sub

' ----------------------------------------------------------------- foco y teclas

sub setZone(z as String)
    m.prevZone = m.zone
    m.zone = z
    if z = "hero"
        showFeatured()
    else if z = "rows"
        e = rowItem(m.rows.rowItemFocused)
        if e <> invalid then showItem(e, false)
    end if
    updateButtons()
    updateDots()
    updateRail()
    applyFocus()
end sub

sub applyFocus()
    if m.zone = "rows" and m.rowData.Count() > 0
        m.rows.setFocus(true)
    else if m.zone = "rail"
        m.railItemsGroup.setFocus(true)
    else
        m.zone = "hero"
        m.btns.setFocus(true)
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "options"
        showOptions()
        return true
    end if
    if m.zone = "rail"
        if key = "left"
            if m.railIndex > 0
                m.railIndex = m.railIndex - 1
                updateRail()
            end if
            return true
        else if key = "right"
            if m.railIndex < m.railDefs.Count() - 1
                m.railIndex = m.railIndex + 1
                updateRail()
            end if
            return true
        else if key = "OK"
            pressRail()
            return true
        else if key = "down" or key = "back"
            setZone("hero")
            return true
        end if
        return true
    end if
    if m.zone = "hero"
        m.rotTimer.control = "stop"
        if m.featList.Count() > 1 then m.rotTimer.control = "start"
        if key = "left"
            if m.btnIndex > 0
                m.btnIndex = m.btnIndex - 1
                updateButtons()
            end if
            return true
        else if key = "up"
            m.railIndex = 1
            setZone("rail")
            return true
        else if key = "right"
            if m.btnIndex < m.btnNodes.Count() - 1
                m.btnIndex = m.btnIndex + 1
                updateButtons()
            end if
            return true
        else if key = "down"
            if m.rowData.Count() > 0 then setZone("rows")
            return true
        else if key = "OK"
            pressButton()
            return true
        end if
        return false
    end if
    ' Filas: lo que la lista no usa llega aquí.
    if key = "up" or key = "back"
        setZone("hero")
        return true
    else if key = "left"
        return true
    end if
    return false
end function

' ----------------------------------------------------------------- reloj y cuenta

sub refreshInfo()
    now = nowSecs()
    m.clock.text = fmtHM(now)
    m.dateLabel.text = fmtDateEs(now)
    s = m.global.session
    if s = invalid or s.account = invalid
        m.accPill.visible = false
        m.accDot.visible = false
        m.accText.visible = false
        return
    end if
    acc = s.account
    line = "Cuenta activa"
    m.accDot.color = "0x38D0FFFF"
    if acc.exp > 0
        days = (acc.exp - now) \ 86400
        if days < 0
            line = "Cuenta vencida"
            m.accDot.color = "0xFF5A8AFF"
        else
            line = "Cuenta activa · vence " + fmtDateEs(acc.exp)
        end if
    end if
    m.accText.text = line
    w = m.accText.boundingRect().width
    pillW = w + 46
    x = 1232 - pillW
    m.accPill.translation = [x, 86]
    m.accPill.width = pillW
    m.accDot.translation = [x + 16, 97]
    m.accText.translation = [x + 32, 86]
    m.accPill.visible = true
    m.accDot.visible = true
    m.accText.visible = true
end sub

' ----------------------------------------------------------------- tecla * (opciones): cerrar sesión

sub showOptions()
    dlg = CreateObject("roSGNode", "Dialog")
    dlg.title = "Opciones"
    s = m.global.session
    if s <> invalid and s.user <> invalid then dlg.message = "Cuenta: " + toS(s.user)
    dlg.buttons = ["Cerrar sesión", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onOptionsChoice")
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onOptionsChoice()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    dlg.close = true
    m.dialog = invalid
    applyFocus()
    if choice = 0 then m.top.event = { type: "logout" }
end sub
