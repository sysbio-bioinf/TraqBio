(function() {
    var orderFormChanged = false,
        sampleSheetChanged = false,
        oldproject = {
                projectsteps: [],
                customers: [],
                notifiedusers: {}
        };
        project = {
            projectsteps: [],
            customers: [],
            notifiedusers: {}
        };

    var stepBody, stepTemplate,
    	prevTitleEdit = null,
    	templateRow,
    	newStepId = 0;

    var customerTable,
        customerTableBody,
        customerTemplateRow;

    $(document).ready(function () {

        initializeProject();

        stepBody = $('#stepsAccordion');
        stepTemplate = $('#project_step').html();
        customerTemplateRow = $('#customer_template_row').html();

        customerTable = $('#customer-notification-table');
        customerTableBody = $('#customer-notification-table tbody');

        $('.customize').on('change', projectDataBinding);

        $('#orderform').on('change', function() { orderFormChanged = true;});

        $('#samplesheet').on('change', function() { sampleSheetChanged = true;});

        $('#updateProject').on('click', putProject);

        $('#exportTemplate').on('click', saveAsTemplate);

        $('#addCustomerRow').off('click').on('click', function(e) { addCustomerRow({}); });

        templateRow = $('#template_row').html();

        redraw();

        // initialize customer selection dialog
        var customerSelectionTableDom = $('#customer-table');
        var customerSelectionTable = customerSelectionTableDom.DataTable({
            paginate: true,
            ordering: true,
            info:     false
        });

        $('tbody', customerSelectionTableDom).on('click', 'tr', function () {
            if ( $(this).hasClass('selected') ) {
                $(this).removeClass('selected');
            }
            else {
                $('tr.selected', customerSelectionTableDom).removeClass('selected');
                $(this).addClass('selected');
            }
        });

        function selectCustomer(row){
            var customer = customerSelectionTable.row(row).data();

            var customerData = {email: customer[1], name: customer[0]}

            addCustomerRow( customerData );
            redrawCustomers();

            $('.select-customer').modal('toggle');
            $('tr.selected', customerSelectionTableDom).removeClass('selected');
        }

        $('tbody', customerSelectionTableDom).delegate('tr', 'dblclick', function(){
            selectCustomer(this);
        });

        $('#btn-select').on('click', function(){
            selectCustomer('.selected');
        });

        $('#btn-close').on('click', function(){
        	$('tr.selected', customerSelectionTableDom).removeClass('selected');
        });
        // END initialize customer selection dialog

        var $moduletable = $('#moduletable').attr('value'); //gets actual table as string maps from :moduletable in clj
        //{:id 92, :template 5, :step 1, :name "SeqMod1", :text "textSeq\nStep1"} is present in $moduletable
        //TODO: Selection based on template can not be the problem, since P2 has same basis as P1 but doesn't show dropdown -> Problem must be link to Project id / routing
        AppendOptions2Dropdown($moduletable);//Appends all options to their step-specific dropdown-menus

    }); //end of document.ready

    function AppendOptions2Dropdown(moduletable){
            var modarray = moduletable.split(/[{}]/); //yields array with 2*N+1 entries for all N textmodules, saved in positions 1,3,5,...,2*N-1
            var N = modarray.length; var i;
            var $projtemplid = $('#projtemplid').attr('value').split(/[ }]/)[1]; //correctly gives out template number to which project belongs, compare this with mod-template
            for (i = 1; i < N; i=i+2) {//loop correctly gets all individual maps/modules (i.e. content inside {})
                var module = modarray[i]; //typeof => string
                var module_template = parseInt(module.split(':template').pop().split(',')[0]);
                if (module_template == $projtemplid){//only get those modules which belong to the current project
                    //correctly extracts entries for step, name and text
                    var module_id = parseInt(module.split(':id').pop().split(',')[0]);
                    var module_step = parseInt(module.split(':step').pop().split(',')[0]);
                    var module_name = module.split(':name').pop().split(',')[0];
                        var module_name = module_name.substr(1,module_name.length -1);
                    var module_text = module.split(':text').pop().split(',')[0];
                    //data-id counts steps across all projects, data-sequence counts steps from 1
                    $('.AccStepSelector[data-sequence="' + module_step + '"]').append($('<option>', {
                        value: module_id,
                        text: module_name.slice(1,-1),
                        descr: module_text
                    }));
                }
            } 
    }//end of AppendOptions2Dropdown function, called once when page first renders

    function initializeProject(){
        $('.project-init').each(function(){
            var name  = $(this).data().name;

            var value = $(this).text();

            if( name === "notifycustomer" ){
            	value = parseInt( value );
            }

            project[name] = value;
            oldproject[name] = value;
        });
        // create empty project steps
        $('.project-steps-init').each(createProjectStep);
        // sort by sequence number
        project.projectsteps.sort(compareSteps);
        oldproject.projectsteps.sort(compareSteps);
        // fill project steps with data
        $('.project-steps-init').each(projectStepInit);
        $('.users-init').each(usersInit);
        initCustomers();
    }

    function initCustomers(){
    	var initElements = $('.customers-init');
    	var n = initElements.length;

    	project.customers = new Array(n);
    	oldproject.customers = new Array(n);

    	initElements.each(
			function(){
				var data = $(this).data();
				var sequence = parseInt(data.sequence);
				var customerData = {};
				var oldCustomerData = {};
				$(this).find('div').each(
						function(){
							customerData[ $(this).data().name ] = $(this).text();
							oldCustomerData[ $(this).data().name ] = $(this).text();
						});

				project.customers[ sequence ] = customerData;
				oldproject.customers[ sequence ] = oldCustomerData;
			});
    }

    function addCustomerRow(customerData) {
        project.customers.push(customerData);

        var formgroup = customerTableBody.closest(".form-group");
    	formgroup.removeClass('has-error');
    	customerTable.find("th").removeClass('has-error');

        redraw();
    }

    function deleteCustomerRow(e){
    	var todelete = $(this).data().index;
    	project.customers.splice( todelete, 1 );
    	redraw();
    }

    function createProjectStep(){
        console.log("Called createProjectStep in editProject.js:");
    	var id    = $(this).data().id;
    	var sequence    = $(this).data().sequence;
    	project.projectsteps.push({id: id, sequence: sequence});
        oldproject.projectsteps.push({id: id, sequence: sequence});
    }

    function projectStepInit(){
        var idx  = $(this).data().sequence - 1;

        var step = project.projectsteps[ idx ];
        var oldstep = oldproject.projectsteps[ idx ];

        $(this).find("div").each(function(){
            var name = $(this).data().name,
                val;

            if (name === "state" || name === "sequence" ){
                val = parseInt($(this).text())
            }else if ( name === "iscurrent"){
            	val = ( $(this).text() === 'true' );
            }else {
                val = $(this).text();
            }

            step[name] = val;
            if( name === "state")
            	step["prevstate"] = val;
            oldstep[name] = val;
        });

        if( !('iscurrent' in step) ){
        	step['iscurrent'] = false;
        	oldstep['iscurrent'] = false;
        }

    }

    function usersInit(){
        var username = $(this).data().username;
        var notifyvalue =  parseInt($(this).text());
        project.notifiedusers[username] = notifyvalue;
        oldproject.notifiedusers[username] = notifyvalue;
    }

    function projectDataBinding(e) {
        var name  = $(this).data().name;
        var value = $(this).val();

        if( name == "usernotification" )
        {
        	var username = $(this).data().username;
        	project.notifiedusers[username] = (this.checked ? 1 : 0);
        }
        else if ( name == "notifycustomer" )
        {
        	project[ name ] = (this.checked ? 1 : 0);
        }
        else
        {
        	project[name] = value;
        }
    }

    function projectStepDataBinding(e) {
        var name  = $(this).data().name;
        var id    = $(this).data().id;
        var val;
        if ($(this).is(":checkbox")) {
            val = $(this).is(':checked') ? 1 : 0;
        } else {
            val =  $(this).val();
        }

        for (var key in project.projectsteps) {
            if (project.projectsteps[key].id == id) {
                project.projectsteps[key][name] = val;
            }
        }
    }

    function updateOrderForm() {
        var orderForm = $('#orderform')[0];
        if (orderForm.value) {
            var filename = orderForm.files[0].name.replace(/C:\\fakepath\\/i,'');
            project.orderform = filename;
            var data = new FormData();
            data.append('file', orderForm.files[0]);
            data.append('trackingnr', project.trackingnr);
            $.ajax({
                url: serverRoot + '/prj/create/upload',
                type: 'POST',
                data: data,
                cache: false,
                contentType: false,
                processData: false
            });
        }
    }

    function updateSampleSheet() {
        var sampleSheet = $('#samplesheet')[0];
        if (sampleSheet.value) {
            var filename = sampleSheet.files[0].name.replace(/C:\\fakepath\\/i,'');
            project.samplesheet = filename;
            var data = new FormData();
            data.append('file', sampleSheet.files[0]);
            data.append('trackingnr', project.trackingnr)
            $.ajax({
                url: serverRoot + '/prj/create/upload',
                type: 'POST',
                data: data,
                cache: false,
                contentType: false,
                processData: false
            });
        }
    }

    function checkForDuplicates(array) {
        return new Set(array).size !== array.length
      }

    function tableData($table) {
        var data = $table.rows().data();
        var rowCount = data.length;
        var rowData = [];
        for(i = 0; i < rowCount; i++) {
            rowData[i] = data[i];
        }
        return rowData;
    }

    function putProject(e) { //NOTE: This function is being called when updateProject button is clicked
        console.log("Calling putProject from js");
        console.log(project.projectsteps);
        console.log(project.projectsteps.length); 
        var typevector = new Array(project.projectsteps.length);
        for (let i = 0; i < project.projectsteps.length; i++){
            typevector[i] = project.projectsteps[i]['type'];
        }
        var duplicateCheck = checkForDuplicates(typevector);
        console.log(duplicateCheck);
        if (duplicateCheck){
            // 'type' = name of a project step -> ensures uniqueness of names of all steps
            alert("There should not be duplicates in the names of template steps. \n \
            Please change the name of this step before updating the project.");
        } 




        var checkpassed = fnCheckForRestrictedWords(printerror=true);
    	$('#projectData').find(".has-error").removeClass('has-error');

    	var formgroup = customerTable.closest(".form-group");
    	var helpblock = formgroup.find('.help-block.with-errors');
    	helpblock.empty();

		var formErrors = $('#projectData').validator('validate').find('.has-error');
		var customerTableError = $( customerTableBody ).find("tr").length == 0;
		if (formErrors.length > 0) {
             $('html, body').animate({
                 scrollTop: $(formErrors[0]).closest(".form-group").offset().top
             }, 300);
		}
		else if (customerTableError)
        {
        	formgroup.addClass('has-error');
        	customerTable.find("th").addClass('has-error');

            helpblock.append("Please add at least one customer");

        	$('html, body').animate({
                scrollTop: formgroup.offset().top
            }, 300);
        }
		else{
			if (orderFormChanged)
	            updateOrderForm();

	        if (sampleSheetChanged)
	            updateSampleSheet();
            
            //if checkpassed is true, execute ajax, if not remain on the same page
            if (checkpassed == true){
                $.ajax({
                    url: serverRoot + '/prj/edit/' + project.id,
                    type: 'PUT',
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify({project: project, oldproject: oldproject}),
                    success: success
                });
            }
         }
    }//end of PutProject function

    function success(e) {
        window.location = serverRoot + '/prj/edit'
    }

    var renderProjectSteps = function(body, project) {

        $.each(project.projectsteps, function(index, step){

        	// marks new state
        	if( step.state == 1 ){
        		step['done'] = true;

        	}else{
        		delete step['done'];
        	}

        	// coloring and controls based on previous state
        	if( step.prevstate == 1 ){
        		step['prevdone'] = true;
        		step.panel_state = "panel-success";
        	}else{
        		delete step['prevdone'];
        		step.panel_state = "";
        	}

        	if( step.iscurrent ){
        		step.collapse = "collapse in";
        		step.panel_state = "panel-info";
        	}else
        		step.collapse = "collapse";

        	if( step.type )
        		step.name = step.type;
        	else
        		step.name = "Step-".concat(index);

            var row = Mustache.render(stepTemplate, step);
            body.append(row);
        });
    };

    function bindHandler() {
    	$('.customizeStep').on('change', projectStepDataBinding);
    	stepBody.find('.btn-add-row').on('click', addRow);
        stepBody.find('.btn-delete-row').on('click', deleteRow);
        stepBody.find('.btn-row-up').on('click', upRow);
        stepBody.find('.btn-row-down').on('click', downRow);
        // init inline edit of step titles
        $.fn.editable.defaults.mode = 'inline';
        stepBody.find('.step-title').editable({
            type: 'text',
            send: 'never',
            title: 'Modify step name',
            inputclass: "edit-textbox"
        });
        $(document).on('click', '.editable-cancel, .editable-submit', function(e){
        	e.stopPropagation();
            $('.edit-step-title').show();
        })
        stepBody.find('.edit-step-title').on( 'click', initStepTitleEdit );
        //console.log("Callind bindHandler: Old value entry in text field is:")
        //TODO: Print editable-textbox entry here, use this value in updateStepTitle
            //const val = document.querySelector('input').value;

        stepBody.find('.step-title').on('save', updateStepTitle);
        stepBody.find('.step-title').on('hidden', restoreEditButton);

        $('[data-toggle="tooltip"]').tooltip();
    }

    function customerDataBinding(e){
        var index = $(this).data().index;
        var name = $(this).data().name;
        var value = $(this).val();

        project.customers[index][name] = value;
    }

    function renderCustomers(customerTableBody, project) {
        $.each(project.customers, function(index, customer){
        	customer.index = index;
            var row = Mustache.render(customerTemplateRow, customer);
            customerTableBody.append(row);
        });
        customerTableBody.find('.btn-delete-customer-row').on('click', deleteCustomerRow);
        customerTableBody.find('input').on('change', customerDataBinding);
    }

    function redrawCustomers() {
    	customerTableBody.empty();
        renderCustomers(customerTableBody, project);
    }

    function redraw() {
        stepBody.empty();
        renderProjectSteps(stepBody, project);
        bindHandler();
        redrawCustomers();
    }

    function compareSteps(f, s) {
        return (f.sequence < s.sequence) ? -1 : 1;
    }

    function restoreEditButton(e) {
    	e.stopPropagation();

    	// in case of previous edits, restore their edit buttons
    	if( prevTitleEdit != null )
    		stepBody.find( "#".concat(prevTitleEdit) ).show();
    }

    function initStepTitleEdit(e){
    	e.stopPropagation();

    	// necessary for switching between editing of two step titles
    	restoreEditButton(e);

        console.log(document.getElementById(e.currentTarget.id));

    	prevTitleEdit = $(this)[0].id;
    	var stepid = $(this).data().stepid;
    	stepBody.find( stepid ).editable('toggle');
        $(this).hide();
    }

    function updateStepTitle(e, params){
        //Get all step titles, only update if new value would not be a duplicate, otherwise give warning
        var typevector = new Array(project.projectsteps.length);
        for (let i = 0; i < project.projectsteps.length; i++){
            typevector[i] = project.projectsteps[i]['type'];
        }

        e.stopPropagation();
        var steps = project.projectsteps,
            titleid = e.currentTarget.id;
        var seq = stepBody.find( "#".concat( titleid ) ).data().sequence;

        console.log("logs inside updateStepTitle function:");
        console.log(titleid); //title_step_6
        //title_step_6 is the id of <a ...>step03</a> -> how to get this value?
        //Get value inside edit-textbox class object
        console.log(document.getElementById(titleid)); //contains entire <a ...>NEW TEXT FIELD VALUE </a>

        typevector[seq - 1 ] = params.newValue; //Add new name, then check for duplicate
        var duplicateCheck = checkForDuplicates(typevector);
        if (duplicateCheck){
            alert("There should not be duplicates in the 'Type' of template steps.");
            //TODO: Trigger same action as if X button had been clicked instead of checkmark
        } else {
    	steps[ seq - 1 ].type = params.newValue;
        }//no duplicate, update step title
    }


    function addRow (e){
        console.log("Called addRow in editProject.js:");
        console.log(project);
        console.log(project.template);
        //TODO: Only show this alert if project is based on a template
        //TODO: Check id and associated template number in the "project" database table
        //var templatenrfromdb = 0; //TODO: Get :template entry associated with ProjNum from :project db table -> if this is not NULL, print the warning
        var ProjNum = project.projectnumber; //e.g. "P-1"
        //var $moduletable = $('#moduletable').attr('value'); 
        //console.log($moduletable);
        alert("Adding a step to a project based on a template may lead to a mismatch in text modules associated with this template. \nIt is recommended to create a new template with this additional step first to base the project on.");
    	// event handled, do not propagate to parents
    	e.stopPropagation();

        var newidx = $(this).data().sequence,
        	steps = project.projectsteps,
        	n = steps.length;

        newStepId --;

        steps.push({
            id: newStepId,
            type: "NEW",
            description:"",
            sequence: n + 1,
            state: 0
        })

        for(var i = n; i > newidx; i--){
        	swap( steps, i - 1, i );
        }

        redraw();
    }

    function deleteRow (e){
        console.log("Called deleteRow in editProject.js:");
    	// event handled, do not propagate to parents
    	e.stopPropagation();

        var idx = $(this).data().sequence - 1,
        	steps = project.projectsteps,
        	iscurrent = steps[idx].iscurrent;

        for(var i = idx + 1; i < steps.length; i++){
        	steps[i-1] = steps[i];
        	steps[i-1].sequence = i;
        }

        steps.pop();
        if( idx < steps.length )
        	steps[idx].iscurrent = iscurrent;

        redraw();
    }


    function swap(steps, i, j) {
    	var tmp = steps[i];
    	steps[i] = steps[j];
    	steps[j] = tmp;
    	steps[i].sequence = i+1;
    	steps[j].sequence = j+1;
    	var iscurrentTmp = steps[i].iscurrent;
    	steps[i].iscurrent = steps[j].iscurrent;
    	steps[j].iscurrent = iscurrentTmp;
    }


    function upRow(e) {
        console.log("Called upRow in editProject.js:");
    	// event handled, do not propagate to parents
    	e.stopPropagation();

        var seq = $(this).data().sequence,
            currentIdx = seq - 1,
            newIdx = currentIdx - 1,
            steps = project.projectsteps;

        if (seq != 1 && steps[newIdx].state == 0) {
        	swap( steps, currentIdx, newIdx );
            redraw();
        }
    }

    function downRow(e) {
        console.log("Called downRow in editProject.js:");
    	// event handled, do not propagate to parents
    	e.stopPropagation();

        var seq   = $(this).data().sequence,
            currentIdx = seq - 1,
            newIdx = currentIdx + 1,
            steps = project.projectsteps;

        if (seq != steps.length) {
            swap( steps, currentIdx, newIdx);
            redraw();
        }
    }


    var successTemplate = '<div class="alert alert-success"><% message %></div>';
    var errorTemplate = '<div class="alert alert-danger"><% message %></div>';

    function saveFailed(msg) { console.log("failure!");
        var alert;
        if (msg.hasOwnProperty("responseJSON"))  {
            alert = {message: "Failure: " + msg.responseJSON.error };
        }
        else if (msg.hasOwnProperty("responseText")) {
            alert = {message: "Failure: " + msg.responseText };
        }
        else {
            alert = {message: "Unknown reason."};
        }

        var content = $('#after-creation-content');
        content.html(Mustache.render(errorTemplate, alert));
        $('.after-creation').modal();
    }

    function templateSaved(msg){ console.log("success!");
        var alert = { message: "Success" };
        var content = $('#after-creation-content');
        content.before(Mustache.render(successTemplate, alert));
        $('.after-creation').modal();
    }

    function saveAsTemplate() {
        var name = $('#templateName').val();
        if (name) {
        	// fast deep copy according to http://stackoverflow.com/questions/122102/what-is-the-most-efficient-way-to-clone-an-object/5344074#5344074
        	var template = JSON.parse(JSON.stringify(project));
            template.name = name;
            template.templatesteps = template.projectsteps;
            //TODO: Save default free text that was entered into template
            delete template.projectsteps;

            $.ajax({
                url: serverRoot + '/template/create',
                type: 'POST',
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify(template),
                success: templateSaved,
                error: saveFailed
            });
        }
    }

}());
