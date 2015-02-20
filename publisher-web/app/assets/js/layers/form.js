require ([
	'dojo/dom',
	'dojo/on',
	'dojo/_base/window',
	'dojo/query',
	'dojo/dom-attr',
	'dojo/dom-construct',
	'tree-select/tree-select',
	'put-selector/put',
	
	'dojo/domReady!'
], function (dom, on, win, query, domattr, domConstruct, TreeSelect, put) {
	var treeSelect = new TreeSelect ('.gp-tree-select', '.gp-tree-values');
	
	/*
	<div class="keyword-item-block">
		<span class="keyword-item label label-primary">Recreatie
			<button type="button" aria-hidden="true" class="close">&times;</button>
		</span>
	</div>
	*/
	
	
	var keywordbutton = dom.byId('add-keyword');
	var keywordlist = dom.byId('keyword-list');
	
	on(keywordbutton,'click', function(evt) {
		var keywordinput = dom.byId('input-keywords').value;
		
		if(keywordinput !== "") {
			var el1 = put("div.keyword-item-block");
			var el2 = put(el1, "div.keyword-item.label.label-primary", keywordinput);
			var el3 = put(el2, "button.[type=button][aria-hidden=true].close");
			el3.innerHTML = "&times;";
			
			put(keywordlist, el1);
		}
		
		dom.byId('input-keywords').value = "";
	});
});

$(function () {
	$('[data-toggle="popover"]').popover();
});