sub init()
    m.bg = m.top.findNode("bg")
    m.fallback = m.top.findNode("fallback")
    m.art = m.top.findNode("art")
    m.logo = m.top.findNode("logo")
    m.shade = m.top.findNode("shade")
    m.name = m.top.findNode("name")
    m.progBg = m.top.findNode("progBg")
    m.progFill = m.top.findNode("progFill")
    m.corners = m.top.findNode("corners")
end sub

sub onContent()
    layout()
end sub

sub layout()
    c = m.top.itemContent
    if c = invalid then return
    w = m.top.width
    h = m.top.height
    if w <= 0 then w = 124
    if h <= 0 then h = 186
    style = "poster"
    if c.hasField("xStyle") then style = c.xStyle
    pct = 0.0
    if c.hasField("xProgress") then pct = c.xProgress
    title = c.title
    uri = c.HDPosterUrl

    m.bg.width = w
    m.bg.height = h
    m.corners.width = w
    m.corners.height = h

    if style = "channel"
        m.art.visible = false
        m.fallback.visible = false
        m.logo.visible = true
        m.logo.translation = [w * 0.22, 12]
        m.logo.width = w * 0.56
        m.logo.height = h - 52
        m.logo.loadWidth = Int(w * 0.84)
        m.logo.loadHeight = Int((h - 52) * 1.5)
        m.logo.uri = uri
        m.shade.visible = true
        m.shade.translation = [0, h - 44]
        m.shade.width = w
        m.shade.height = 44
        m.name.visible = true
        m.name.translation = [10, h - 34]
        m.name.width = w - 20
        m.name.height = 28
        m.name.text = title
        m.progBg.visible = false
        m.progFill.visible = false
        return
    end if

    m.logo.visible = false
    m.name.visible = false
    m.fallback.visible = true
    m.fallback.translation = [8, 0]
    m.fallback.width = w - 16
    m.fallback.height = h
    m.fallback.text = title
    m.art.visible = true
    m.art.width = w
    m.art.height = h
    m.art.loadWidth = Int(w * 1.5)
    m.art.loadHeight = Int(h * 1.5)
    m.art.uri = uri
    if pct > 0.01
        m.shade.visible = true
        m.shade.translation = [0, h - 30]
        m.shade.width = w
        m.shade.height = 30
        m.progBg.visible = true
        m.progFill.visible = true
        m.progBg.translation = [8, h - 12]
        m.progBg.width = w - 16
        m.progBg.height = 4
        if pct > 1 then pct = 1
        m.progFill.translation = [8, h - 12]
        m.progFill.width = (w - 16) * pct
        m.progFill.height = 4
    else
        m.shade.visible = false
        m.progBg.visible = false
        m.progFill.visible = false
    end if
end sub
