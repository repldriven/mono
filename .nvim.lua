-- .nvim.lua
---@diagnostic disable: undefined-global
local lspconfig = require("lspconfig")

-- Customize yamlls settings
lspconfig.yamlls.setup({
	settings = {
		yaml = {
			customTags = {
				"!env",
				"!keyword",
				"!include",
				"!port",
				"!profile mapping",
				"!strs mapping",

				"!pulsar/access-mode",
				"!pulsar/crypto-failure-action",
				"!pulsar/message-id",
				"!pulsar/schema",
				"!pulsar/subscription-type",

				"!system/component mapping",
				"!system/local-ref",
				"!system/ref",
				"!system/required-component",
			},
		},
	},
})

-- Format Clojure files with zprint on save (using fast babashka script)
vim.api.nvim_create_autocmd("BufWritePost", {
	pattern = { "*.clj", "*.cljs", "*.cljc", "*.edn" },
	callback = function()
		local file = vim.fn.expand("%:p")
		local project_root = vim.fn.getcwd()
		local script = project_root .. "/.zprint-format.bb"
		if vim.fn.filereadable(script) == 1 then
			vim.fn.system(script .. " " .. vim.fn.shellescape(file))
			vim.cmd("checktime") -- Reload the file if it changed
		end
	end,
	desc = "Format Clojure files with zprint via babashka",
})

-- Disable JSON formatting for Avro schema files
vim.api.nvim_create_autocmd("BufEnter", {
	pattern = "*.avsc.json",
	callback = function()
		-- Disable formatting for this buffer
		vim.b.disable_autoformat = true

		-- If LSP is already attached, disable its formatting capability
		local clients = vim.lsp.get_active_clients({ bufnr = 0 })
		for _, client in ipairs(clients) do
			if client.name == "jsonls" then
				client.server_capabilities.documentFormattingProvider = false
			end
		end
	end,
	desc = "Disable formatting for Avro schema files",
})

-- Disable conform.nvim formatting for Avro schema files
local conform_ok, _ = pcall(require, "conform")
if conform_ok then
	-- Override format_on_save to skip .avsc.json files
	vim.api.nvim_create_autocmd("BufWritePre", {
		pattern = "*",
		callback = function(args)
			local bufname = vim.api.nvim_buf_get_name(args.buf)
			if bufname:match("%.avsc%.json$") then
				-- Don't format this file
				return
			end
		end,
		desc = "Skip conform.nvim for Avro schema files",
	})
end

-- Also prevent any format commands from working on these files
vim.api.nvim_create_autocmd("FileType", {
	pattern = "json",
	callback = function()
		local bufname = vim.api.nvim_buf_get_name(0)
		if bufname:match("%.avsc%.json$") then
			vim.bo.formatexpr = ""
			vim.bo.formatprg = ""
		end
	end,
	desc = "Disable all formatting for Avro schema files",
})

-- Format slides.md with local prettier-plugin-slidev
vim.api.nvim_create_autocmd({ "BufRead", "BufNewFile" }, {
	pattern = "*/slides.md",
	callback = function()
		vim.bo.filetype = "markdown.slidev"
	end,
	desc = "Set slidev filetype for slides.md",
})
