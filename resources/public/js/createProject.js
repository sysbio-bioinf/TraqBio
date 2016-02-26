(function(){
    $(document).ready(function() {
        $('.hover-popover').popover({
            trigger: 'hover'
        });

        $('.newTemplateToggle').on('click', function(){
            $('#newTemplateWrapper').toggle('fast');
        });

        if (!Array.prototype.last){
            Array.prototype.last = function(){
                return this[this.length - 1];
            };
        }

        var templateRow = $('#template_row').html();
        var customerTemplateRow = $('#customer_template_row').html();        
        var successTemplate = '<div class="alert alert-success"><% message %></div>';
        var errorTemplate = '<div class="alert alert-danger"><% message %></div>';
        
        var idCounter = 0;
        var template = null;

        var tBody = $('#step-table tbody');
        var customerTable = $('#customer-notification-table');
        var customerTableBody = $('#customer-notification-table tbody');
        var generalDescription = $('#generalDescription');

        
        function creatingFailed(msg) {
            var alert;
            if (msg.hasOwnProperty("responseJSON"))  {

                alert = {message: "Failure: " + msg.responseJSON.error };

                if (msg.responseJSON.trackingnr) {

                    uploadOrderForm(msg.responseJSON.trackingnr);

                    uploadSampleSheet(msg.responseJSON.trackingnr);
                }
            }
            else if (msg.hasOwnProperty("responseText")) {
                alert = {message: "Failure: " + msg.responseText };
            }
            else {
                alert = {message: "Unknown reason."};
            }

            var content = $('#after-creation-content');
            content.html(Mustache.render(errorTemplate, alert));
            $('.after-creation')
                .modal()
                .on('hidden.bs.modal', function (e) {
                    window.location = serverRoot + '/';
                });
        }

        function projectCreated(msg){
            var alert = { message: "Success" };
            var content = $('#after-creation-content');
            content.before(Mustache.render(successTemplate, alert));
            
            var prj = {
            	customerinfos: msg.customerinfos.join("<br>"),
            	'customer': function (x){ return x;},
                trackingNr: msg.trackingnr,
                trackingLink: msg.trackinglink,
                emailerror: msg.emailerror
            };
            
            content.html(Mustache.render($('#success-message').html(), prj));

            uploadOrderForm(msg.trackingnr);

            uploadSampleSheet(msg.trackingnr);

            $('.after-creation')
                .modal()
                .on('hidden.bs.modal', function (e) {
                    window.location = serverRoot + '/';
                });
        }

        var renderProjectSteps = function(tBody, template) {
            $.each(template.templatesteps, function(index, step){
                var row = Mustache.render(templateRow, step);
                tBody.append(row);
            });
        };
       

        function compareSteps(f, s) {
            return (f.sequence < s.sequence) ? -1 : 1;
        }

        function deleteTemplateStep (id, template) {
            var newSteps = [],
                steps = template.templatesteps;

            steps.sort(compareSteps);
            var seq = 1;
            for (var i = 0; i <  steps.length; i++) {
                if (steps[i].id != id) {
                    steps[i].sequence = seq++;
                    newSteps.push(steps[i]);
                }
            }
            template.templatesteps = newSteps;
        }

        function normalizeSteps(template) {

            for (var key in template.templatesteps) {
                delete template.templatesteps[key]['id'];
                template.templatesteps[key].state = 0;
            }

            return template;
        }


        function uploadOrderForm(nr) {
            var orderForm = $('#orderForm')[0];
            if (orderForm.value) {
                var data = new FormData();
                data.append('file', orderForm.files[0]);
                data.append('trackingnr', nr);
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

        function uploadSampleSheet(nr) {
            var sampleSheet = $('#sampleSheet')[0];
            if (sampleSheet.value) {
                var data = new FormData();
                data.append('file', sampleSheet.files[0]);
                data.append('trackingnr', nr);
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

        
        var templateLoaded = function(loadedtemplate) {
        	template =loadedtemplate;
            
            if( !('notifiedusers' in template) )
            	template.notifiedusers = {};
            
            generalDescription.find('.customize').each(
            		function(){
            			var name = $(this).data().name;
            			if( name in template )
            				$(this).val( template[name] );
            			else{
            				if( name == "usernotification" )
                            {
                            	var username = $(this).data().username;
                            	template.notifiedusers[username] = (this.checked ? 1 : 0);
                            }
                            else if ( name == "notifycustomer" )
                            {
                            	template[ name ] = (this.checked ? 1 : 0);
                            }
                            else
                            {
                            	template[name] = $(this).val();
                            }
            			}
            			});
            
            
            var customerCount = $( customerTableBody ).find("tr").length;
            template.customers = new Array( customerCount );
            
            // preserve already entered customer data
            customerTableBody.find('.customer-data').each(
            		function(){
            			var data = $(this).data();
            			
            			if( !(data.index in template.customers) ) 
            				template.customers[data.index] = {};
            			
            			var customerData = template.customers[data.index];
            			customerData[data.name] = $(this).val();
            		});
            
            tBody.empty();

            redraw();
            //renderProjectSteps(tBody, template);
            

            $('.btn-add-row').off('click').on('click', addRow);
            generalDescription.find('.customize').on('change', bodyDataBinding);
            bindHandler();
            $('#addCustomerRow').off('click').on('click', function(e) { addCustomerRow({}); });            
            
            $('#startProject').off('click').on('click', startProject);
        };

        $('#selectTemplate').on('change', function(e) {
            var id = parseInt(e.currentTarget.value , 10);
            if (id > 0) {
                $.ajax({
                    url: serverRoot + '/template/' + id,
                    type: 'GET',
                    contentType: "application/json; charset=utf-8",
                    success: templateLoaded
                });
            } else {
                templateLoaded({
                    description:"",
                    templatesteps: [],
                    customers: []
                });
            }
        });
        
        function bindHandler() {
            tBody.find('.btn-delete-row').on('click', deleteRow);
            tBody.find('.btn-row-up').on('click', upRow);
            tBody.find('.btn-row-down').on('click', downRow);
            tBody.find('input, textarea').on('change', stepDataBinding);
        }

        function bodyDataBinding(e) {
            var name  = $(this).data().name;
            var value = $(this).val();
            if (name == 'orderform' || name == 'samplesheet') {
                value = value.replace(/C:\\fakepath\\/i,'');
            }
                            
            if( name == "usernotification" )
            {
            	var username = $(this).data().username;
            	template.notifiedusers[username] = (this.checked ? 1 : 0);
            }
            else if ( name == "notifycustomer" )
            {
            	template[ name ] = (this.checked ? 1 : 0);
            }
            else
            {
            	template[name] = value;
            }
        }

        function stepDataBinding(e){
            var value = $(this).val(),
                name  = $(this).data().name,
                id    = $(this).data().id,
                steps  = template.templatesteps;

            for (var i = 0; i <  steps.length; i++) {
                if (steps[i].id == id) {
                    steps[i][name] = value;
                }
            }
        }
        
        
        function customerDataBinding(e){
            var index = $(this).data().index;
            var name = $(this).data().name;
            var value = $(this).val();
            
            template.customers[index][name] = value;
        }
        
        function renderCustomers(customerTableBody, template) {
            $.each(template.customers, function(index, customer){
            	customer.index = index;
                var row = Mustache.render(customerTemplateRow, customer);
                customerTableBody.append(row);
            });
            customerTableBody.find('.btn-delete-customer-row').on('click', deleteCustomerRow);
            customerTableBody.find('input').on('change', customerDataBinding);
        }

        function redrawCustomers() {
        	customerTableBody.empty();
            renderCustomers(customerTableBody, template);
        }
        
        function redraw() {
            tBody.empty();
            renderProjectSteps(tBody, template);
            bindHandler();
            redrawCustomers();
        }

        function deleteRow (e){
            var id = $(this).data().id;
            deleteTemplateStep(id, template);
            redraw();
        }

        function addRow(e) {
            var last = template.templatesteps.last(),
                newSeq;
            if (last) {
                newSeq = last.sequence + 1;
            } else {
                newSeq = 1;
            }

            template.templatesteps.push({
                id: --idCounter,
                type: "",
                description:"",
                sequence: newSeq
            });
            redraw();
        }
        function upRow(e) {
            var seq    = $(this).data().sequence,
                before = seq - 1,
                steps  = template.templatesteps;
            if (seq != 1) {
                for (var i = 0; i <  steps.length; i++) {
                    if (steps[i].sequence == before) {
                        steps[i].sequence = steps[i].sequence + 1;
                    } else if (steps[i].sequence == seq) {
                        steps[i].sequence = steps[i].sequence - 1;
                    }
                }
                steps.sort(compareSteps);
                template.templatesteps = steps;
                redraw();
            }
        }
        function downRow(e) {
            var seq   = $(this).data().sequence,
                after = seq + 1,
                steps = template.templatesteps;
            if (seq != steps.length) {
                for (var i = 0; i <  steps.length; i++) {
                    if (steps[i].sequence == seq) {
                        steps[i].sequence = steps[i].sequence + 1;
                    } else if (steps[i].sequence == after) {
                        steps[i].sequence = steps[i].sequence - 1;
                    }
                }
                steps.sort(compareSteps);
                template.templatesteps = steps;
                redraw();
            }
        }
        
        function addCustomerRow(customerData) {
            template.customers.push(customerData);
                        
            var formgroup = customerTableBody.closest(".form-group");
        	formgroup.removeClass('has-error');
        	customerTable.find("th").removeClass('has-error');
        	
            redraw();
        }
        
        function deleteCustomerRow(e){
        	var todelete = $(this).data().index;
        	template.customers.splice( todelete, 1 );
        	redraw();
        }

        function createNewTemplate() {
            var name = $('#newTemplate').val();
            if (name) {
                template.name = name;
                $.ajax({
                    url: serverRoot + '/template/create',
                    type: 'POST',
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify(normalizeSteps(template))
                });
            }
        }

        function startProject(e) {
        	$('#generalDescription').find(".has-error").removeClass('has-error');
        	
        	var formgroup = customerTable.closest(".form-group");
        	var helpblock = formgroup.find('.help-block.with-errors'); 
        	helpblock.empty();
        	
            var formErrors = $('#generalDescription').validator('validate').find('.has-error');
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
                createNewTemplate(template);

                $.ajax({
                    url: serverRoot + '/prj/create',
                    type: 'POST',
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify(normalizeSteps(template)),
                    success: projectCreated,
                    error: creatingFailed
                });
            }
        }
        
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
        	$('tr.selected', customerSelectionTableDom).removeClass('selected');
            selectCustomer(this);
        });

        $('#btn-select').on('click', function(){
            selectCustomer('.selected');
        });
        
        $('#btn-close').on('click', function(){
        	$('tr.selected', customerSelectionTableDom).removeClass('selected');
        });
        // END initialize customer selection dialog

        templateLoaded({
            description:"",
            templatesteps: [],
        	customers: []
        });        
    });
}());
