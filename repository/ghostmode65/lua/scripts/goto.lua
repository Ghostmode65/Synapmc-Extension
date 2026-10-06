Chat:getCommandManager():unregisterCommand("goto")
Client:waitTick(1)

local success, saves = pcall(function()
local script = loadfile(synapmc:roaming().."/config/homes.lua") return script and script() or nil end)
    if not (success and saves) then saves = {Save = {}} end

Chat:getCommandManager():createCommandBuilder("goto"):quotedStringArg("name")
:suggest(JavaWrapper:methodToJava(
    function(ctx, suggestionBuilder)
        for name,_ in pairs(saves) do suggestionBuilder:suggest(name) end
    end))
:executes(JavaWrapper:methodToJava(
function(context)
    local input = context:getArg("name")
        if not input then Chat:log("§c[Goto] §fName missing") return nil end
        if input == "Save" or input == "save" then Chat:log("§c[Goto] §fAdd a name to save") return nil end
        if not saves[input] then Chat:log("§c[Goto] §fNot home with that name exist") return nil end
    Chat:log("§a[Goto] §fTeleporting to "..input)
    Player:getPlayer():setPos(table.unpack(saves[input]))
end))
:wordArg("Save")
:executes(JavaWrapper:methodToJava(
    function(context)
        local _serpent = load(synapmc:repo(nil):getString("Lua Serpent"))()
        local pos = Player:getPlayer():getPos()
        local input = context:getArg("Save")
        saves[input] = {pos:getX(),pos:getY(),pos:getZ()}
        Chat:log("§a[Goto] Saved "..input)
        _serpent.save("/config/homes.lua",saves)
    end))
:register()